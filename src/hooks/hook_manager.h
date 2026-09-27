// ============================================================================
//  woke.wtf — src/hooks/hook_manager.h
//
//  MinHook-style function hooking on Linux, backed by funchook (v1.1.3,
//  vendored in third_party/). Phase 1 decision: upstream MinHook is
//  Windows-only, so this is the Linux hook engine; modules use this shim API
//  so the graphics/input hook code in later phases reads exactly like
//  MinHook (MH_CreateHook / MH_EnableHook / MH_DisableHook semantics).
//
//  All calls are defensive: failures log via core/logger and return false —
//  nothing aborts the host process.
// ============================================================================

#pragma once

#include <cstddef>

namespace woke::hooks {

/// One-shot global init (creates the funchook instance). Returns false when
/// the underlying engine could not be prepared.
bool init();

/// Release all hooks and the engine. Safe to call twice.
void shutdown();

/// Install a detour on `target`; on success *trampoline receives the
/// original function pointer (call it from inside the detour).
/// `name` is used for logging only.
bool install(void *target, void *detour, void **trampoline, const char *name);

/// Re-enable a previously installed hook (e.g. after unload-reload cycles).
bool enable(void *target);

/// Temporarily disable a hook without removing it.
bool disable(void *target);

/// Remove a hook entirely (restores the original bytes).
bool remove(void *target);

/// Diagnostic: install a detour on a dummy function for one second, verify
/// the detour fired, remove the hook, and log the verdict. Called from the
/// bootstrap init chain to prove the engine works inside this process.
/// Returns true when the self-test passed.
bool self_test();

} // namespace woke::hooks
