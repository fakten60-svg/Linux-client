// ============================================================================
//  woke.wtf — src/jvm/base_jni_hook.cpp
//  See base_jni_hook.h for the design contract.
// ============================================================================

#include "jvm/base_jni_hook.h"

#include "core/logger.h"

namespace woke::jvm {
namespace {

constexpr const char *kTag = "jni";

JavaVM *g_vm = nullptr;

} // namespace

// ---------------------------------------------------------------------------
// ScopedAttach
// ---------------------------------------------------------------------------

ScopedAttach::ScopedAttach(JavaVM *vm) : vm_(vm) {
    if (vm_ == nullptr) {
        return;
    }
    const jint res = vm_->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8);
    if (res == JNI_OK) {
        return; // already attached
    }
    if (res == JNI_EDETACHED) {
        JavaVMAttachArgs args{};
        args.version = JNI_VERSION_1_8;
        args.name = const_cast<char *>("woke-init");
        if (vm_->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&env), &args) == JNI_OK) {
            attached_ = true;
        } else {
            log::error(kTag, "ScopedAttach: AttachCurrentThreadAsDaemon failed");
        }
    } else {
        log::error(kTag, "ScopedAttach: GetEnv failed (res=%d)", static_cast<int>(res));
    }
}

ScopedAttach::~ScopedAttach() {
    if (attached_ && vm_ != nullptr) {
        vm_->DetachCurrentThread();
    }
}

// ---------------------------------------------------------------------------
// BaseJNIHook
// ---------------------------------------------------------------------------

bool BaseJNIHook::init(JavaVM *vm) {
    if (vm == nullptr) {
        log::error(kTag, "init: null JavaVM");
        return false;
    }
    g_vm = vm;
    return true;
}

void BaseJNIHook::shutdown() {
    g_vm = nullptr;
}

JavaVM *BaseJNIHook::vm() { return g_vm; }

JNIEnv *BaseJNIHook::env() {
    if (g_vm == nullptr) {
        return nullptr;
    }
    JNIEnv *env = nullptr;
    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK) {
        return nullptr;
    }
    return env;
}

jclass BaseJNIHook::find_class(JNIEnv *env, const char *intermediary_name) {
    if (env == nullptr || intermediary_name == nullptr) {
        return nullptr;
    }
    jclass cls = env->FindClass(intermediary_name);
    if (cls == nullptr) {
        log::warn(kTag, "FindClass failed: %s", intermediary_name);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
    return cls;
}

jmethodID BaseJNIHook::method_id(JNIEnv *env, jclass cls, const char *intermediary,
                                 const char *signature, bool *out_is_static) {
    if (env == nullptr || cls == nullptr) {
        return nullptr;
    }
    // Static-first: see header note on HotSpot's GetMethodID quirk.
    jmethodID mid = env->GetStaticMethodID(cls, intermediary, signature);
    if (mid != nullptr) {
        if (out_is_static != nullptr) {
            *out_is_static = true;
        }
        return mid;
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    mid = env->GetMethodID(cls, intermediary, signature);
    if (mid == nullptr) {
        log::warn(kTag, "GetMethodID failed: %s %s", intermediary, signature);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return nullptr;
    }
    if (out_is_static != nullptr) {
        *out_is_static = false;
    }
    return mid;
}

jfieldID BaseJNIHook::field_id(JNIEnv *env, jclass cls, const char *intermediary,
                               const char *signature, bool *out_is_static) {
    if (env == nullptr || cls == nullptr) {
        return nullptr;
    }
    jfieldID fid = env->GetStaticFieldID(cls, intermediary, signature);
    if (fid != nullptr) {
        if (out_is_static != nullptr) {
            *out_is_static = true;
        }
        return fid;
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    fid = env->GetFieldID(cls, intermediary, signature);
    if (fid == nullptr) {
        log::warn(kTag, "GetFieldID failed: %s %s", intermediary, signature);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        return nullptr;
    }
    if (out_is_static != nullptr) {
        *out_is_static = false;
    }
    return fid;
}

bool BaseJNIHook::exception_ok(JNIEnv *env, const char *what) {
    if (env == nullptr || !env->ExceptionCheck()) {
        return true;
    }
    env->ExceptionDescribe();
    env->ExceptionClear();
    log::error(kTag, "pending JNI exception at %s (cleared)", what != nullptr ? what : "?");
    return false;
}

bool BaseJNIHook::object_of(JNIEnv *env, jobject obj, jclass cls) {
    if (env == nullptr || obj == nullptr || cls == nullptr) {
        return false;
    }
    const jboolean is = env->IsInstanceOf(obj, cls);
    if (!exception_ok(env, "IsInstanceOf")) {
        return false;
    }
    return is == JNI_TRUE;
}

jobject BaseJNIHook::new_global_ref(JNIEnv *env, jobject local) {
    if (env == nullptr || local == nullptr) {
        return nullptr;
    }
    jobject g = env->NewGlobalRef(local);
    if (g == nullptr) {
        log::error(kTag, "NewGlobalRef failed (OOM?)");
    }
    return g;
}

} // namespace woke::jvm
