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
#include <cstdlib>
#include <cstring>

namespace woke {

constexpr const char *kTag = "boot";

/// Signature of libjvm.so's JNI_GetCreatedJavaVMs (resolved at runtime).
using GetCreatedJavaVMsFn = jint (JNICALL *)(JavaVM **, jsize, jsize *);

namespace {

constexpr long kDefaultBootTimeoutMs = 30000;

JavaVM *volatile g_vm                 = nullptr;
volatile bool    g_jvm_ready          = false;
volatile bool    g_bootstrap_complete = false;
pthread_t        g_bootstrap_thread   = {};

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
    log::info(kTag, "bootstrap thread running (pid=%d tid=%lu)", getpid(),
              static_cast<unsigned long>(pthread_self()));

    const auto get_created_vms = reinterpret_cast<GetCreatedJavaVMsFn>(
        dlsym(RTLD_DEFAULT, "JNI_GetCreatedJavaVMs"));
    if (get_created_vms == nullptr) {
        log::warn(kTag, "JNI_GetCreatedJavaVMs not resolvable — not a JVM process?");
        g_bootstrap_complete = true;
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
            g_bootstrap_complete = true;
            return nullptr;
        }
        const timespec ts = {0, kPollMs * 1000 * 1000L};
        nanosleep(&ts, nullptr);
        waited_ms += kPollMs;
    }

    g_vm = vm;
    log::info(kTag, "JVM discovered — running init chain");
    run_jvm_chain(vm);
    g_jvm_ready = true;
    g_bootstrap_complete = true;
    return nullptr;
}

} // namespace

// ---------------------------------------------------------------------------
// Public accessors (used by later-phase subsystems)
// ---------------------------------------------------------------------------

JavaVM *java_vm() { return g_vm; }
bool jvm_ready() { return g_jvm_ready; }
bool bootstrap_complete() { return g_bootstrap_complete; }

void set_jvm(JavaVM *vm) {
    if (vm != nullptr) {
        g_vm = vm;
        g_jvm_ready = true;
    }
}

void join_bootstrap() {
    if (g_bootstrap_thread != pthread_t{}) {
        pthread_join(g_bootstrap_thread, nullptr);
        g_bootstrap_thread = pthread_t{};
    }
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
    const int rc = pthread_create(&woke::g_bootstrap_thread, nullptr, &woke::bootstrap_main, nullptr);
    if (rc != 0) {
        woke::log::error(woke::kTag, "bootstrap thread spawn failed (%s)", std::strerror(rc));
        return;
    }
    pthread_detach(woke::g_bootstrap_thread);
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

JNIEXPORT void JNICALL JNI_OnUnLoad(JavaVM * /*vm*/, void * /*reserved*/) {
    woke::join_bootstrap();
    woke::dispatch::shutdown();
    woke::jvm::GameContext::shutdown();
    woke::jvm::ReflectionCache::shutdown();
    woke::jvm::BaseJNIHook::shutdown();
    woke::jvm::mappings::unload();
    woke::hooks::shutdown();
    woke::log::info("jni", "JNI_OnUnLoad: subsystems released, libwoke.so detaching");
    woke::log::shutdown();
}

} // extern "C"
