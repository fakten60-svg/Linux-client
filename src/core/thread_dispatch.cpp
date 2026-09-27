// ============================================================================
//  woke.wtf — src/core/thread_dispatch.cpp
//  See thread_dispatch.h for the design contract.
//
//  Fixed-capacity ring of slots — no heap allocation anywhere in the queue.
//  post() rejects when full (never blocks producers); the worker drains in
//  batches so tasks always run unlocked (tasks may post() without deadlock).
//
//  Two deliberate choices, both learned from live-JVM teardown behaviour:
//
//    * Raw pthread primitives with static initialisers instead of
//      std::mutex / std::condition_variable. They are constant-initialised, so
//      the queue works no matter when the dynamic loader reaches this
//      translation unit's initialisers, and — critically — they have no
//      destructors. A std::condition_variable runs pthread_cond_destroy() when
//      the module is finalised and glibc blocks that call while a waiter is
//      still parked inside it, which hung dlclose()/JVM shutdown outright.
//
//    * The JVM attachment lives only for the duration of a drain batch. A
//      worker that stayed attached and idle was never released before the
//      launcher tore the library down.
// ============================================================================

#include "core/thread_dispatch.h"

#include "core/logger.h"
#include "entry.h"

#include <pthread.h>

#include <cstring>

#include <atomic>
#include <cstdint>

namespace woke::dispatch {
namespace {

constexpr const char *kTag = "dispatch";

struct Slot {
    TaskFn fn;
    void *user;
    const char *name;
};

Slot g_queue[kQueueCapacity];
pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t g_work_cv = PTHREAD_COND_INITIALIZER;
std::size_t g_head = 0;
std::size_t g_count = 0;
bool g_initialized = false;

std::atomic<bool> g_worker_stop{false};
std::atomic<std::size_t> g_executed{0};
std::atomic<std::size_t> g_dropped{0};
pthread_t g_worker{};
bool g_worker_running = false;

/// Pop one task. Returns false when the queue is empty.
bool pop_one(Slot *out) {
    pthread_mutex_lock(&g_mutex);
    if (g_count == 0) {
        pthread_mutex_unlock(&g_mutex);
        return false;
    }
    *out = g_queue[g_head];
    g_head = (g_head + 1) % kQueueCapacity;
    --g_count;
    pthread_mutex_unlock(&g_mutex);
    return true;
}

/// Attach the calling thread to the JVM as a daemon for the lifetime of this
/// object. Attachment is per drain batch on purpose: a permanently attached
/// thread holds JVM state that delays VM teardown.
struct ScopedJniAttach {
    JavaVM *vm = nullptr;
    JNIEnv *env = nullptr;
    bool owned = false;

    ScopedJniAttach() = default;
    ScopedJniAttach(const ScopedJniAttach &) = delete;
    ScopedJniAttach &operator=(const ScopedJniAttach &) = delete;

    ScopedJniAttach(ScopedJniAttach &&other) noexcept
        : vm(other.vm), env(other.env), owned(other.owned) {
        other.vm = nullptr;
        other.owned = false;
    }
    ScopedJniAttach &operator=(ScopedJniAttach &&) = delete;

    ~ScopedJniAttach() {
        if (owned && vm != nullptr) {
            vm->DetachCurrentThread();
        }
    }
};

ScopedJniAttach attach_current() {
    ScopedJniAttach attach;
    attach.vm = java_vm();
    if (attach.vm == nullptr) {
        return attach;
    }
    if (attach.vm->GetEnv(reinterpret_cast<void **>(&attach.env), JNI_VERSION_1_8) == JNI_OK) {
        return attach; // already attached (a JVM thread posted the work)
    }
    JavaVMAttachArgs args{};
    args.version = JNI_VERSION_1_8;
    args.name = const_cast<char *>("woke-dispatch");
    if (attach.vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&attach.env), &args) !=
        JNI_OK) {
        attach.env = nullptr;
        // Expected while the JVM itself is tearing down: the module finaliser
        // stops the worker and the remaining tasks run JVM-free.
        if (!g_worker_stop.load(std::memory_order_acquire)) {
            log::warn(kTag, "worker attach failed — running JVM-free tasks only");
        }
    } else {
        attach.owned = true;
    }
    return attach;
}

/// Worker body: drain the queue until shutdown().
void *worker_main(void *) {
    {
        const ScopedJniAttach probe = attach_current();
        log::info(kTag, "worker thread online (jvm_attached=%s)",
                  probe.env != nullptr ? "yes" : "no");
    }

    while (true) {
        pthread_mutex_lock(&g_mutex);
        while (g_count == 0 && !g_worker_stop.load(std::memory_order_acquire)) {
            pthread_cond_wait(&g_work_cv, &g_mutex);
        }
        const bool stopping = g_worker_stop.load(std::memory_order_acquire);
        pthread_mutex_unlock(&g_mutex);
        if (stopping) {
            break;
        }

        const ScopedJniAttach attach = attach_current();
        drain(attach.env);
    }

    { // final sweep so shutdown never loses accepted tasks
        const ScopedJniAttach attach = attach_current();
        drain(attach.env);
    }

    log::info(kTag, "worker thread exiting (executed=%zu dropped=%zu)", g_executed.load(),
              g_dropped.load());
    return nullptr;
}

} // namespace

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

void init() {
    pthread_mutex_lock(&g_mutex);
    if (!g_initialized) {
        g_head = 0;
        g_count = 0;
        g_initialized = true;
    }
    pthread_mutex_unlock(&g_mutex);
}

bool post(TaskFn fn, void *user, const char *name) {
    if (fn == nullptr) {
        return false;
    }
    pthread_mutex_lock(&g_mutex);
    if (!g_initialized || g_count >= kQueueCapacity) {
        if (g_initialized) {
            g_dropped.fetch_add(1);
        }
        pthread_mutex_unlock(&g_mutex);
        return false; // hot paths must degrade, never block
    }
    const std::size_t tail = (g_head + g_count) % kQueueCapacity;
    g_queue[tail] = Slot{fn, user, name};
    ++g_count;
    pthread_mutex_unlock(&g_mutex);
    pthread_cond_signal(&g_work_cv);
    return true;
}

void drain(JNIEnv *env) {
    Slot task{};
    while (pop_one(&task)) {
        task.fn(env, task.user);
        g_executed.fetch_add(1);
    }
}

bool start_worker() {
    pthread_mutex_lock(&g_mutex);
    if (!g_initialized) {
        pthread_mutex_unlock(&g_mutex);
        return false;
    }
    if (g_worker_running) {
        pthread_mutex_unlock(&g_mutex);
        return true;
    }
    g_worker_stop.store(false);
    const int rc = pthread_create(&g_worker, nullptr, &worker_main, nullptr);
    if (rc != 0) {
        log::error(kTag, "worker spawn failed (%s)", strerror(rc));
        pthread_mutex_unlock(&g_mutex);
        return false;
    }
    g_worker_running = true;
    pthread_mutex_unlock(&g_mutex);
    return true;
}

void shutdown() {
    bool joined = false;
    pthread_mutex_lock(&g_mutex);
    if (g_worker_running) {
        g_worker_stop.store(true, std::memory_order_release);
        joined = true;
    }
    pthread_mutex_unlock(&g_mutex);

    if (joined) {
        pthread_cond_broadcast(&g_work_cv);
        pthread_join(g_worker, nullptr);
        pthread_mutex_lock(&g_mutex);
        g_worker_running = false;
        pthread_mutex_unlock(&g_mutex);
    }

    drain(nullptr);

    pthread_mutex_lock(&g_mutex);
    g_initialized = false;
    pthread_mutex_unlock(&g_mutex);
}

std::size_t executed_count() { return g_executed.load(); }
std::size_t dropped_count() { return g_dropped.load(); }

std::size_t queued_count() {
    pthread_mutex_lock(&g_mutex);
    const std::size_t n = g_count;
    pthread_mutex_unlock(&g_mutex);
    return n;
}

/// Module finaliser: the worker must never outlive the library. Without this
/// the parked worker would still be sitting on the queue's condvar when the
/// launcher unmaps us.
__attribute__((destructor)) static void dispatch_module_fini() {
    shutdown();
}

} // namespace woke::dispatch
