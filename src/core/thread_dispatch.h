// ============================================================================
//  woke.wtf — src/core/thread_dispatch.h
//
//  Game-thread task queue — the C++ analogue of Minecraft's mc.execute().
//
//    * post(fn): enqueue from any thread. Allocation-free on the steady
//      state: a fixed-capacity slot array (kQueueCapacity) with mutex +
//      condvar handoff. post() rejects (returns false) when full — hot
//      paths must degrade gracefully, never block the game.
//    * drain(env): run every pending task on the CURRENT thread. This is
//      the future entry point called from the graphics hook each frame;
//      today the dedicated worker thread calls it in a loop.
//    * Worker thread: starts with start_worker() and drains until shutdown().
//      It attaches to the JVM as a daemon (using java_vm() from entry.cpp)
//      only for the duration of each drain batch — an attached thread holds
//      JVM state, so the worker releases it while idle. Tasks receive the
//      attached JNIEnv*, or nullptr when no JVM is available.
//
//    * The module finaliser calls shutdown(), so the worker can never outlive
//      the library (or be left parked on a condvar being torn down).
// ============================================================================

#pragma once

#include <jni.h>

#include <cstddef>

namespace woke::dispatch {

using TaskFn = void (*)(JNIEnv *env, void *user);

constexpr std::size_t kQueueCapacity = 256;

/// Initialize the queue. Must precede post()/start_worker().
void init();

/// Stop the worker, drain remaining tasks, free queue memory.
void shutdown();

/// Enqueue a task from any thread. False when the queue is full or
/// shutting down (callers must degrade gracefully).
bool post(TaskFn fn, void *user = nullptr, const char *name = nullptr);

/// Run all currently-queued tasks on the calling thread (game-thread path).
/// `env` may be null for JVM-free tasks.
void drain(JNIEnv *env);

/// Start the dedicated worker thread. It attaches to the JVM (daemon) and
/// drains the queue until shutdown(). Returns false when unavailable.
bool start_worker();

/// Worker statistics (diagnostics).
std::size_t executed_count();
std::size_t dropped_count();

/// Snapshot of queue depth (diagnostics).
std::size_t queued_count();

} // namespace woke::dispatch
