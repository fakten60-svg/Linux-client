// ============================================================================
//  woke.wtf — src/entry.h
//
//  Accessors exported by entry.cpp for other subsystems: the authoritative
//  JavaVM* pointer, JVM readiness, and bootstrap-thread control.
// ============================================================================

#pragma once

#include <jni.h>

namespace woke {

/// Authoritative JavaVM* (null before the JVM is discovered/registered).
JavaVM *java_vm();

/// True once a JavaVM* is available (bootstrap thread or JNI_OnLoad).
bool jvm_ready();

/// True when the bootstrap thread reached a terminal state.
bool bootstrap_complete();

/// Register the JVM from JNI_OnLoad (idempotent).
void set_jvm(JavaVM *vm);

/// Join the bootstrap thread (unload path — code must not unmap under it).
void join_bootstrap();

} // namespace woke
