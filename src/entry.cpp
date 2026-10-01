// ============================================================================
//  woke.wtf — src/entry.cpp
//
//  Library entry point and init-chain orchestrator. Two load paths:
//
//    1. JVM-side load (System.loadLibrary): JVM_OnLoad hands us the VM.
//    2. Early injection (constructor attribute): a bootstrap thread polls
//       JNI_GetCreatedJavaVMs until the JVM exists, then runs the chain.
//
//  Phase 3 init chain (each stage logs its verdict):
//    logger → mappings.json → funchook self-test → wait for JVM →
//    BaseJNIHook/ScopedAttach → ReflectionCache → GameContext.
//
//  The JVM symbol is resolved via dlsym(RTLD_DEFAULT, ...) so the .so links
//  cleanly with -Wl,--no-undefined and early loads never fail — we wait.
// ============================================================================

#include "core/event_bus.h"
#include "core/logger.h"
#include "core/thread_dispatch.h"
#include "hooks/hook_manager.h"
#include "jvm/base_jni_hook.h"
#include "jvm/game_context.h"
#include "jvm/mappings.h"
#include "jvm/reflection_cache.h"
#include "entry.h"

#include <jni.h>

#include <dlfcn.h>
#include <pthread.h>
#include <time.h>
#include <unistd.h>

#include <atomic>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace woke {

constexpr const char *kTag = "boot";

/// Signature of libjvm.so's JNI_GetCreatedJavaVMs (resolved at runtime).
using GetCreatedJavaVMsFn = jint (JNICALL *)(JavaVM **, jsize, jsize *);

namespace {

constexpr long kDefaultBootTimeoutMs = 30000;

// Shared between the constructor's bootstrap thread and JNI_OnLoad. These are
// std::atomic, not `volatile`: volatile only stops the compiler folding the
// accesses, it does nothing at all for two threads racing on them. This was
// literally `volatile` until ThreadSanitizer flagged g_vm and g_jvm_ready being
// written from both sides with no synchronisation.
std::atomic<JavaVM *>  g_vm{nullptr};
std::atomic<bool>      g_jvm_ready{false};
std::atomic<bool>      g_bootstrap_complete{false};
std::atomic<pthread_t> g_bootstrap_thread{pthread_t{}};

// Teardown has to wait for the bootstrap thread to stop touching the
// subsystems before they are shut down. The thread itself is detached (so a
// library that is never unloaded retains nothing), which rules out
// pthread_join — so it signals this condition variable instead, exactly once,
// on every exit path.
//
// pthread primitives with static initialisers, not std::mutex /
// std::condition_variable: the latter own a destructor that runs at static
// destruction time, and that destructor races the detached thread's final
// signal (ThreadSanitizer caught exactly this). These have no destructor, so
// there is nothing to race. Same reason thread_dispatch.cpp uses them.
pthread_mutex_t g_bootstrap_wait_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  g_bootstrap_wait       = PTHREAD_COND_INITIALIZER;
bool            g_bootstrap_finished   = false;   // guarded by the mutex

/** RAII: announces that the bootstrap thread is finished, on any exit path. */
struct SignalBootstrapDone {
    ~SignalBootstrapDone() {
        pthread_mutex_lock(&g_bootstrap_wait_mutex);
        g_bootstrap_finished = true;
        pthread_mutex_unlock(&g_bootstrap_wait_mutex);
        pthread_cond_broadcast(&g_bootstrap_wait);
    }
};

// ---------------------------------------------------------------------------
// Dispatch demonstration task — executed on the dedicated worker thread.
// Proves the full Phase 4 chain: a NON-game thread (bootstrap) posts a task,
// the worker dequeues it, attaches to the JVM, calls a REAL JVM method
// (java.lang.System.getProperty), and publishes an event that a subscribed
// listener receives.
// ---------------------------------------------------------------------------

void on_client_started(const event_bus::ClientStartedEvent &e, void * /*user*/) {
    log::info("events", "ClientStartedEvent received: %s", e.message);
}

void dispatch_demo_task(JNIEnv *env, void * /*user*/) {
    char message[192];

    // Call a real JVM method through the attached JNIEnv: System.getProperty.
    bool jvm_call_ok = false;
    if (env != nullptr) {
        jclass sys = jvm::BaseJNIHook::find_class(env, "java/lang/System");
        if (sys != nullptr) {
            const jmethodID get_prop = jvm::BaseJNIHook::method_id(
                env, sys, "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
            if (get_prop != nullptr) {
                const jstring key = env->NewStringUTF("java.vm.name");
                const auto result = static_cast<jstring>(
                    env->CallStaticObjectMethod(sys, get_prop, key));
                if (jvm::BaseJNIHook::exception_ok(env, "System.getProperty")) {
                    char value[80] = {0};
                    if (result != nullptr) {
                        const char *utf = env->GetStringUTFChars(result, nullptr);
                        if (utf != nullptr) {
                            snprintf(value, sizeof(value), "%s", utf);
                            env->ReleaseStringUTFChars(result, utf);
                        }
                        env->DeleteLocalRef(result);
                    }
                    snprintf(message, sizeof(message),
                             "worker thread JVM call result: java.vm.name=%s", value);
                    jvm_call_ok = true;
                }
                if (key != nullptr) {
                    env->DeleteLocalRef(key);
                }
            }
            env->DeleteLocalRef(sys);
        }
    }
    if (!jvm_call_ok) {
        snprintf(message, sizeof(message), "worker thread ran (no JVM call available)");
    }

    log::info("demo", "%s", message);

    // Publish through the event bus — the subscribed listener logs receipt.
    event_bus::ClientStartedEvent ev{message};
    event_bus::publish(ev);
}

long boot_timeout_ms() {
    const char *env = std::getenv("WOKE_BOOT_TIMEOUT_MS");
    if (env == nullptr) {
        return kDefaultBootTimeoutMs;
    }
    char *end = nullptr;
    const long parsed = std::strtol(env, &end, 10);
    if (end == env || *end != '\0' || parsed < 0) {
        return kDefaultBootTimeoutMs;
    }
    return parsed;
}

/// Runs the full subsystem chain on a live JVM. Called with the mappings,
/// hooks and logger stages already done.
/// Single-entry gate: both the constructor bootstrap thread and JNI_OnLoad
/// can discover the JVM — exactly one of them runs the chain.
void run_jvm_chain(JavaVM *vm) {
    static std::atomic<bool> chain_started{false};
    bool expected = false;
    if (!chain_started.compare_exchange_strong(expected, true)) {
        log::info(kTag, "init chain already started — skipping duplicate entry");
        return;
    }
    if (!jvm::BaseJNIHook::init(vm)) {
        return;
    }
    jvm::ScopedAttach attach(vm);
    if (attach.env == nullptr) {
        log::error(kTag, "could not attach init thread to the JVM");
        return;
    }

    if (!jvm::ReflectionCache::init(attach.env)) {
        log::error(kTag, "reflection cache init failed");
        return;
    }
    if (jvm::GameContext::init(attach.env)) {
        jvm::GameContext::refresh(attach.env);
        log::info(kTag, "game context: client=%p player=%p world=%p",
                  jvm::GameContext::client_instance(),
                  jvm::GameContext::player_instance(),
                  jvm::GameContext::world_instance());
    }

    // Phase 4: game-thread dispatch. Start the dedicated worker (attaches to
    // the JVM), then post the demonstration task from THIS thread — which is
    // the bootstrap thread, i.e. NOT the game thread.
    if (dispatch::start_worker()) {
        const bool posted = dispatch::post(&dispatch_demo_task, nullptr, "phase4-demo");
        log::info(kTag, "dispatch: demo task %s from bootstrap thread (non-game)",
                  posted ? "posted" : "REJECTED");
    }
}

/// Bootstrap thread body: wait for the JVM, then run the init chain.
void *bootstrap_main(void *) {
    SignalBootstrapDone signal_done;
    log::info(kTag, "bootstrap thread running (pid=%d tid=%lu)", getpid(),
              static_cast<unsigned long>(pthread_self()));

    const auto get_created_vms = reinterpret_cast<GetCreatedJavaVMsFn>(
        dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs"));
    if (get_created_vms == nullptr) {
        log::warn(kTag, "JNI_GetCreatedJavaVMs not resolvable — not a JVM process?");
        g_bootstrap_complete.store(true, std::memory_order_release);
        return nullptr;
    }

    const long timeout_ms = boot_timeout_ms();
    constexpr long kPollMs = 100;
    long waited_ms = 0;

    JavaVM *vm = nullptr;
    while (true) {
        jsize vm_count = 0;
        if (get_created_vms(&vm, 1, &vm_count) == JNI_OK && vm_count > 0 && vm != nullptr) {
            break;
        }
        if (waited_ms >= timeout_ms) {
            log::warn(kTag, "no JVM appeared within %ld ms — idling passively", timeout_ms);
            g_bootstrap_complete.store(true, std::memory_order_release);
            return nullptr;
        }
        const timespec ts = {0, kPollMs * 1000 * 1000L};
        nanosleep(&ts, nullptr);
        waited_ms += kPollMs;
    }

    g_vm.store(vm, std::memory_order_release);
    log::info(kTag, "JVM discovered — running init chain");
    run_jvm_chain(vm);
    g_jvm_ready.store(true, std::memory_order_release);
    g_bootstrap_complete.store(true, std::memory_order_release);
    return nullptr;
}

} // namespace

// ---------------------------------------------------------------------------
// Public accessors (used by later-phase subsystems)
// ---------------------------------------------------------------------------

JavaVM *java_vm() { return g_vm.load(std::memory_order_acquire); }
bool jvm_ready() { return g_jvm_ready.load(std::memory_order_acquire); }
bool bootstrap_complete() { return g_bootstrap_complete.load(std::memory_order_acquire); }

void set_jvm(JavaVM *vm) {
    if (vm != nullptr) {
        g_vm.store(vm, std::memory_order_release);
        g_jvm_ready.store(true, std::memory_order_release);
    }
}

void join_bootstrap() {
    // Detached, so there is no pthread_join to call: wait for the handshake
    // instead. Joining a detached thread is undefined and fails outright.
    if (g_bootstrap_thread.load(std::memory_order_acquire) == pthread_t{}) {
        return;   // never spawned — nothing to wait for
    }
    pthread_mutex_lock(&g_bootstrap_wait_mutex);
    while (!g_bootstrap_finished) {
        pthread_cond_wait(&g_bootstrap_wait, &g_bootstrap_wait_mutex);
    }
    pthread_mutex_unlock(&g_bootstrap_wait_mutex);
    g_bootstrap_thread.store(pthread_t{}, std::memory_order_release);
}

} // namespace woke

// ---------------------------------------------------------------------------
// Constructor bootstrap — runs at dlopen() time
// ---------------------------------------------------------------------------
__attribute__((constructor))
static void woke_constructor() {
    // Stage 1: logging.
    if (woke::log::init()) {
        woke::log::info(woke::kTag, "logger online — session file opened");
    } else {
        woke::log::warn(woke::kTag, "logger online (file sinks unavailable)");
    }
    woke::log::info(woke::kTag, "woke.wtf libwoke.so loaded");

    // Stage 2: mappings.json (auto-schema-detecting loader).
    if (woke::jvm::mappings::load()) {
        woke::log::info(woke::kTag, "mappings: %zu classes, loader schema OK",
                        woke::jvm::mappings::class_count());
    } else {
        woke::log::warn(woke::kTag, "mappings unavailable — JVM resolution degraded");
    }

    // Stage 3: hook engine self-test (proves funchook works in THIS process).
    if (woke::hooks::init() && woke::hooks::self_test()) {
        woke::log::info(woke::kTag, "hook engine verified");
    } else {
        woke::log::warn(woke::kTag, "hook engine self-test failed — hooks degraded");
    }

    // Stage 4a: dispatch queue (worker starts only once the JVM is found).
    woke::dispatch::init();
    woke::event_bus::subscribe<woke::event_bus::ClientStartedEvent>(&woke::on_client_started);

    // Stage 4+: JVM wait and chain — on the bootstrap thread.
    // Detached, so a library that is never unloaded retains no thread resource;
    // teardown still waits for it through the handshake in join_bootstrap().
    // This used to detach *and* join, which is undefined — pthread_join on a
    // detached thread fails outright, so teardown never actually waited.
    pthread_t thread = pthread_t{};
    const int rc = pthread_create(&thread, nullptr, &woke::bootstrap_main, nullptr);
    if (rc != 0) {
        woke::log::error(woke::kTag, "bootstrap thread spawn failed (%s)", std::strerror(rc));
        return;
    }
    woke::g_bootstrap_thread.store(thread, std::memory_order_release);
    pthread_detach(thread);
}

// ---------------------------------------------------------------------------
// JNI exports — the only symbols with default visibility in libwoke.so
// ---------------------------------------------------------------------------
extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    woke::set_jvm(vm);
    woke::log::info("jni", "JNI_OnLoad: JVM handed off — running init chain");
    woke::run_jvm_chain(vm);   // same TU: anonymous-namespace fn, visible via woke::
    return JNI_VERSION_1_8;
}

// The JNI entry point is `JNI_OnUnload` — lowercase "load". It used to be
// spelled JNI_OnUnLoad, which the JVM looks up under the exact spec name and so
// never finds: this whole teardown silently never ran.
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM * /*vm*/, void * /*reserved*/) {
    woke::join_bootstrap();
    woke::dispatch::shutdown();
    woke::jvm::GameContext::shutdown();
    woke::jvm::ReflectionCache::shutdown();
    woke::jvm::BaseJNIHook::shutdown();
    woke::jvm::mappings::unload();
    woke::hooks::shutdown();
    woke::log::info("jni", "JNI_OnUnload: subsystems released, libwoke.so detaching");
    woke::log::shutdown();
}

} // extern "C"
