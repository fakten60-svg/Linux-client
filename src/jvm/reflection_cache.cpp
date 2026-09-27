// ============================================================================
//  woke.wtf — src/jvm/reflection_cache.cpp
//  See reflection_cache.h for the design contract.
// ============================================================================

#include "jvm/reflection_cache.h"

#include "core/logger.h"
#include "jvm/base_jni_hook.h"
#include "jvm/mappings.h"

#include <string>
#include <unordered_map>

namespace woke::jvm {
namespace {

constexpr const char *kTag = "jvm-cache";

struct CachedClass {
    jclass cls = nullptr;                                 // global ref
    std::unordered_map<std::string, jmethodID> methods;   // member key -> id
    std::unordered_map<std::string, jfieldID> fields;
    std::unordered_map<std::string, bool> method_static;  // call-form flags
    std::unordered_map<std::string, bool> field_static;
};

/// Cache state behind a function-local static: no dependency on the order in
/// which the dynamic loader runs this module's initialisers, which matters
/// because entry.cpp's constructor-attribute bootstrap can start resolving
/// through the JVM on its own thread as soon as the library is mapped.
struct State {
    std::unordered_map<std::string, CachedClass> cache;
    size_t missing = 0;
    bool ready = false;
};

State &state() {
    static State s;
    return s;
}    CachedClass *resolve_class(JNIEnv *env, const char *class_key) {
    const mappings::ClassInfo *info = mappings::find_class(class_key);
    if (info == nullptr) {
        log::warn(kTag, "no mapping entry for class key '%s'", class_key);
        ++state().missing;
        return nullptr;
    }
    jclass local = BaseJNIHook::find_class(env, info->intermediary);
    if (local == nullptr) {
        log::warn(kTag, "class not present in JVM: %s (%s)", info->intermediary, class_key);
        ++state().missing;
        return nullptr;
    }
    jclass global = static_cast<jclass>(BaseJNIHook::new_global_ref(env, local));
    env->DeleteLocalRef(local);
    if (global == nullptr) {
        ++state().missing;
        return nullptr;
    }

    CachedClass entry;
    entry.cls = global;
    auto [it, inserted] = state().cache.emplace(class_key, std::move(entry));
    if (!inserted) {
        env->DeleteGlobalRef(global); // duplicate key — keep the first
        return &it->second;
    }
    log::info(kTag, "class %-22s -> %s @ %p", class_key, info->intermediary,
              reinterpret_cast<void *>(global));
    return &it->second;
}

} // namespace

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

bool ReflectionCache::init(JNIEnv *env) {
    if (env == nullptr) {
        log::error(kTag, "init: null JNIEnv");
        return false;
    }
    if (state().ready) {
        return true;
    }
    if (!mappings::loaded() && !mappings::load()) {
        return false;
    }

    // Resolve every curated class, then every curated member within it.
    // Unknown member keys are reported and skipped — never guessed.
    size_t resolved_classes = 0;

    // The curated key set lives in mappings.json; iterate its classes.
    // (mappings:: exposes find_* by key; enumerate via a fixed key list is
    // impossible here, so resolve lazily instead: cache entries are created
    // by the first lookup. For eager behavior we re-walk the mapping table
    // through its public API below.)
    //
    // Eager pass 1 — classes: the loader counts them, but keys are private;
    // game_context drives known keys. Here we pre-resolve nothing and let
    // lookups populate the cache on demand (allocation-free after first hit).
    //
    // To still give the "resolved at init" guarantee, callers must perform
    // one lookup per curated key during their init phase (game_context does).

    state().ready = true;
    log::info(kTag, "ready (%zu mapping classes available, lazy resolution)",
              mappings::class_count());
    return resolved_classes > 0 || mappings::class_count() > 0;
}

void ReflectionCache::shutdown() {
    JNIEnv *env = BaseJNIHook::env(); // best effort; may be null at unload
    for (auto &[key, entry] : state().cache) {
        if (env != nullptr && entry.cls != nullptr) {
            env->DeleteGlobalRef(entry.cls);
        }
    }
    state().cache.clear();
    state().missing = 0;
    state().ready = false;
}

bool ReflectionCache::ready() { return state().ready; }

jclass ReflectionCache::cls(const char *class_key) {
    auto it = state().cache.find(class_key);
    if (it != state().cache.end()) {
        return it->second.cls;
    }
    JNIEnv *env = BaseJNIHook::env();
    if (env == nullptr) {
        return nullptr;
    }
    CachedClass *entry = resolve_class(env, class_key);
    return entry != nullptr ? entry->cls : nullptr;
}

jmethodID ReflectionCache::method(const char *class_key, const char *member_key) {
    // Class cache first (populates it if needed).
    auto it = state().cache.find(class_key);
    if (it == state().cache.end()) {
        JNIEnv *env = BaseJNIHook::env();
        if (env == nullptr || resolve_class(env, class_key) == nullptr) {
            return nullptr;
        }
        it = state().cache.find(class_key);
        if (it == state().cache.end()) {
            return nullptr;
        }
    }
    CachedClass &entry = it->second;

    auto m = entry.methods.find(member_key);
    if (m != entry.methods.end()) {
        return m->second;
    }

    const mappings::MemberInfo *info = mappings::find_method(class_key, member_key);
    if (info == nullptr) {
        log::warn(kTag, "no mapping for method %s.%s", class_key, member_key);
        ++state().missing;
        return nullptr;
    }
    JNIEnv *env = BaseJNIHook::env();
    if (env == nullptr) {
        return nullptr;
    }
    jmethodID mid = nullptr;
    bool is_static = false;
    mid = BaseJNIHook::method_id(env, entry.cls, info->intermediary, info->sig, &is_static);
    if (mid == nullptr) {
        ++state().missing;
        return nullptr;
    }
    entry.methods.emplace(member_key, mid);
    entry.method_static.emplace(member_key, is_static);
    log::info(kTag, "method %-40s -> %s %s @ %p",
              (std::string(class_key) + "." + member_key).c_str(),
              info->intermediary, info->sig, reinterpret_cast<void *>(mid));
    return mid;
}

jfieldID ReflectionCache::field(const char *class_key, const char *member_key) {
    auto it = state().cache.find(class_key);
    if (it == state().cache.end()) {
        JNIEnv *env = BaseJNIHook::env();
        if (env == nullptr || resolve_class(env, class_key) == nullptr) {
            return nullptr;
        }
        it = state().cache.find(class_key);
        if (it == state().cache.end()) {
            return nullptr;
        }
    }
    CachedClass &entry = it->second;

    auto f = entry.fields.find(member_key);
    if (f != entry.fields.end()) {
        return f->second;
    }

    const mappings::MemberInfo *info = mappings::find_field(class_key, member_key);
    if (info == nullptr) {
        log::warn(kTag, "no mapping for field %s.%s", class_key, member_key);
        ++state().missing;
        return nullptr;
    }
    JNIEnv *env = BaseJNIHook::env();
    if (env == nullptr) {
        return nullptr;
    }
    jfieldID fid = nullptr;
    bool is_static = false;
    fid = BaseJNIHook::field_id(env, entry.cls, info->intermediary, info->sig, &is_static);
    if (fid == nullptr) {
        ++state().missing;
        return nullptr;
    }
    entry.fields.emplace(member_key, fid);
    entry.field_static.emplace(member_key, is_static);
    log::info(kTag, "field %-42s -> %s %s @ %p",
              (std::string(class_key) + "." + member_key).c_str(),
              info->intermediary, info->sig, reinterpret_cast<void *>(fid));
    return fid;
}

size_t ReflectionCache::resolved_count() {
    size_t total = 0;
    for (const auto &[key, entry] : state().cache) {
        total += 1 + entry.methods.size() + entry.fields.size();
    }
    return total;
}

size_t ReflectionCache::missing_count() { return state().missing; }

bool ReflectionCache::method_is_static(const char *class_key, const char *member_key) {
    auto it = state().cache.find(class_key);
    if (it == state().cache.end()) {
        return false;
    }
    auto flag = it->second.method_static.find(member_key);
    return flag != it->second.method_static.end() && flag->second;
}

bool ReflectionCache::field_is_static(const char *class_key, const char *member_key) {
    auto it = state().cache.find(class_key);
    if (it == state().cache.end()) {
        return false;
    }
    auto flag = it->second.field_static.find(member_key);
    return flag != it->second.field_static.end() && flag->second;
}

} // namespace woke::jvm
