// ============================================================================
//  woke.wtf — src/jvm/reflection_cache.h
//
//  Centralized registry of cached JNI handles. All jclass/jmethodID/jfieldID
//  handles are resolved ONCE during init (never inside execution loops) from
//  mappings.json keys, and stored as global references / raw JNI ids.
//
//  One-line usage anywhere in the client:
//      jclass  cls = ReflectionCache::cls("minecraft_client");
//      jmethodID m = ReflectionCache::method("minecraft_client", "getInstance");
// ============================================================================

#pragma once

#include <jni.h>

#include <cstddef>

namespace woke::jvm {

class ReflectionCache {
public:
    /// Resolve every curated class + member from mappings.json. Requires an
    /// attached caller thread (ScopedAttach) and a loaded mapping table.
    /// Returns false when no class could be resolved at all.
    static bool init(JNIEnv *env);

    /// Delete global refs and drop all cached ids (unload path).
    static void shutdown();

    static bool ready();

    // --- one-line lookups (allocation-free; safe any time after init) ------
    static jclass cls(const char *class_key);
    static jmethodID method(const char *class_key, const char *member_key);
    static jfieldID field(const char *class_key, const char *member_key);

    /// Whether the cached member resolved as static (CallStatic*/GetStatic*
    /// vs Call*/Get* form). Defaults to false for uncached members.
    static bool method_is_static(const char *class_key, const char *member_key);
    static bool field_is_static(const char *class_key, const char *member_key);

    /// Cache counters for diagnostics.
    static size_t resolved_count();
    static size_t missing_count();
};

} // namespace woke::jvm
