// ============================================================================
//  woke.wtf — src/hooks/hook_manager.cpp
//  See hook_manager.h for the design contract.
//
//  Each hook owns its own funchook instance so enable/disable/remove stay
//  granular (funchook's install/uninstall would otherwise be global across
//  all prepared hooks on a shared instance).
// ============================================================================

#include "hooks/hook_manager.h"

#include "core/logger.h"

#include <funchook.h>

#include <mutex>
#include <unordered_map>

namespace woke::hooks {
namespace {

constexpr const char *kTag = "hooks";

struct HookEntry {
    funchook_t *handle = nullptr;
    void *trampoline = nullptr;
    const char *name = nullptr;
    bool installed = false;
};

/// Registry accessor: function-local static so none of this depends on
/// `.init_array` ordering. entry.cpp's constructor-attribute bootstrap runs
/// before other translation units' dynamic initialisers, and a zeroed
/// std::unordered_map is not a valid empty map (observed: SIGFPE on emplace).
struct Registry {
    std::mutex mutex;
    std::unordered_map<void *, HookEntry> entries;
    bool engine_ok = false;
};

Registry &registry() {
    static Registry r;
    return r;
}

} // namespace

bool init() {
    std::lock_guard<std::mutex> lock(registry().mutex);
    if (registry().engine_ok) {
        return true;
    }
    // Validate that the engine can create an instance at all.
    funchook_t *probe = funchook_create();
    if (probe == nullptr) {
        log::error(kTag, "funchook_create failed — hook engine unavailable");
        return false;
    }
    funchook_destroy(probe);
    registry().engine_ok = true;
    log::info(kTag, "funchook engine ready");
    return true;
}

void shutdown() {
    std::lock_guard<std::mutex> lock(registry().mutex);
    for (auto &[target, entry] : registry().entries) {
        if (entry.handle != nullptr) {
            if (entry.installed) {
                funchook_uninstall(entry.handle, 0);
            }
            funchook_destroy(entry.handle);
        }
    }
    registry().entries.clear();
    registry().engine_ok = false;
    log::info(kTag, "hook engine released");
}

bool install(void *target, void *detour, void **trampoline, const char *name) {
    if (target == nullptr || detour == nullptr) {
        return false;
    }
    if (!init()) {
        return false;
    }
    std::lock_guard<std::mutex> lock(registry().mutex);

    auto [it, inserted] = registry().entries.emplace(target, HookEntry{});
    HookEntry &entry = it->second;
    if (!inserted && entry.installed) {
        if (trampoline != nullptr) {
            *trampoline = entry.trampoline;
        }
        return true; // already hooked — idempotent
    }

    funchook_t *fh = inserted ? funchook_create() : entry.handle;
    if (fh == nullptr) {
        log::error(kTag, "funchook_create failed for %s", name != nullptr ? name : "?");
        registry().entries.erase(it);
        return false;
    }

    void *tramp = target; // in/out: receives the trampoline on success
    int res = funchook_prepare(fh, &tramp, detour);
    if (res != FUNCHOOK_ERROR_SUCCESS) {
        log::error(kTag, "prepare(%s) failed: %s", name != nullptr ? name : "?",
                   funchook_error_message(fh));
        funchook_destroy(fh);
        if (inserted) {
            registry().entries.erase(it);
        }
        return false;
    }
    res = funchook_install(fh, 0);
    if (res != FUNCHOOK_ERROR_SUCCESS) {
        log::error(kTag, "install(%s) failed: %s", name != nullptr ? name : "?",
                   funchook_error_message(fh));
        funchook_destroy(fh);
        if (inserted) {
            registry().entries.erase(it);
        }
        return false;
    }

    entry.handle = fh;
    entry.trampoline = tramp;
    entry.name = name;
    entry.installed = true;
    if (trampoline != nullptr) {
        *trampoline = tramp;
    }
    log::info(kTag, "hooked %-24s target=%p trampoline=%p", name != nullptr ? name : "?",
              target, tramp);
    return true;
}

bool enable(void *target) {
    std::lock_guard<std::mutex> lock(registry().mutex);
    auto it = registry().entries.find(target);
    if (it == registry().entries.end() || it->second.installed) {
        return false;
    }
    const int res = funchook_install(it->second.handle, 0);
    it->second.installed = (res == FUNCHOOK_ERROR_SUCCESS);
    return it->second.installed;
}

bool disable(void *target) {
    std::lock_guard<std::mutex> lock(registry().mutex);
    auto it = registry().entries.find(target);
    if (it == registry().entries.end() || !it->second.installed) {
        return false;
    }
    const int res = funchook_uninstall(it->second.handle, 0);
    if (res == FUNCHOOK_ERROR_SUCCESS) {
        it->second.installed = false;
        return true;
    }
    return false;
}

bool remove(void *target) {
    std::lock_guard<std::mutex> lock(registry().mutex);
    auto it = registry().entries.find(target);
    if (it == registry().entries.end()) {
        return false;
    }
    if (it->second.installed) {
        funchook_uninstall(it->second.handle, 0);
    }
    funchook_destroy(it->second.handle);
    registry().entries.erase(it);
    return true;
}

// ---------------------------------------------------------------------------
// Self-test: hook a local noinline function, verify the detour fires, then
// restore. Proves the engine works inside THIS process before the graphics
// hooks are trusted in later phases.
// ---------------------------------------------------------------------------

namespace {

volatile bool g_selftest_fired = false;

__attribute__((noinline)) int selftest_target() {
    return 7;
}

int selftest_detour() {
    g_selftest_fired = true;
    // For the self-test we only need to prove interception happened; the
    // trampoline path is verified separately below.
    return 42;
}

} // namespace

bool self_test() {
    g_selftest_fired = false;

    void *trampoline = nullptr;
    if (!install(reinterpret_cast<void *>(&selftest_target),
                 reinterpret_cast<void *>(&selftest_detour),
                 &trampoline, "selftest")) {
        log::error(kTag, "self-test: install failed");
        return false;
    }

    // Indirect call so the compiler cannot inline the target away.
    using Fn = int (*)();
    const volatile Fn fn = reinterpret_cast<Fn>(&selftest_target);
    const int hooked_result = fn();

    bool pass = g_selftest_fired && hooked_result == 42;

    if (pass && trampoline != nullptr) {
        // Trampoline must still execute the original body.
        const Fn original = reinterpret_cast<Fn>(trampoline);
        pass = original() == 7;
    }

    remove(reinterpret_cast<void *>(&selftest_target));

    const int restored_result = fn();
    pass = pass && restored_result == 7;

    log::info(kTag, "self-test %s (hooked=%d restored=%d)", pass ? "PASSED" : "FAILED",
              hooked_result, restored_result);
    return pass;
}

} // namespace woke::hooks
