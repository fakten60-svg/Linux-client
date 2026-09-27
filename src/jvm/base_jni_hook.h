// ============================================================================
//  woke.wtf — src/jvm/base_jni_hook.h
//
//  Generic JNI guard layer. Every JNI class/method/field resolution and
//  exception check in the codebase funnels through these static helpers:
//  no raw FindClass/GetMethodID calls outside this file.
//
//  Handles produced here are cached by jvm/reflection_cache — the helpers
//  themselves are one-shot resolution utilities used at init time only.
// ============================================================================

#pragma once

#include <jni.h>

namespace woke::jvm {

/// RAII thread attachment: becomes a JVM thread (daemon) for its scope when
/// the current thread is not already attached. `.env` is usable either way.
class ScopedAttach {
public:
    explicit ScopedAttach(JavaVM *vm);
    ~ScopedAttach();
    ScopedAttach(const ScopedAttach &) = delete;
    ScopedAttach &operator=(const ScopedAttach &) = delete;
    JNIEnv *env = nullptr;
private:
    JavaVM *vm_ = nullptr;
    bool attached_ = false;
};

class BaseJNIHook {
public:
    /// Store the authoritative JavaVM* (idempotent).
    static bool init(JavaVM *vm);

    /// Clear the cached VM pointer (unload path).
    static void shutdown();

    static JavaVM *vm();

    /// GetEnv for the current thread without attaching; null when detached.
    static JNIEnv *env();

    // --- guarded one-shot resolvers (init-time use) -------------------------
    // Resolvers try the STATIC lookup first, then instance — HotSpot returns
    // static methods from GetMethodID, but such an id then rejects
    // CallStatic* with IncompatibleClassChangeError. Callers must use the
    // call form matching *out_is_static.
    static jclass find_class(JNIEnv *env, const char *intermediary_name);
    static jmethodID method_id(JNIEnv *env, jclass cls, const char *intermediary,
                               const char *signature, bool *out_is_static = nullptr);
    static jfieldID field_id(JNIEnv *env, jclass cls, const char *intermediary,
                             const char *signature, bool *out_is_static = nullptr);

    /// True when no exception is pending; otherwise clears it and logs `what`.
    static bool exception_ok(JNIEnv *env, const char *what);

    /// True when `obj` is a non-null instance of `cls`.
    static bool object_of(JNIEnv *env, jobject obj, jclass cls);

    /// New global ref with null/exception guarding; null on failure.
    static jobject new_global_ref(JNIEnv *env, jobject local);
};

} // namespace woke::jvm
