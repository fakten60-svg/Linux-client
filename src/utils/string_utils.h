// ============================================================================
//  woke.wtf — src/utils/string_utils.h
//
//  Header-only text helpers. Kept allocation-free and constexpr-friendly so
//  they can be used from the render loop (search filtering runs every frame
//  over a fixed card list).
//
//  Case folding is ASCII-only on purpose: the UI ships ASCII labels, and a
//  locale-aware fold would pull <locale>/ICU into an injected .so for no gain.
// ============================================================================

#pragma once

#include <cstddef>

namespace woke::text {

/// True when `a` and `b` are equal ignoring ASCII case. Nulls are only equal
/// to each other. Used to parse boolean words out of the settings file
/// ("true"/"on"/"yes" all mean the same thing to a hand-editing user).
inline bool iequals(const char *a, const char *b) {
    if (a == nullptr || b == nullptr) return a == b;
    for (; *a != '\0' && *b != '\0'; ++a, ++b) {
        char ca = *a;
        char cb = *b;
        if (ca >= 'A' && ca <= 'Z') ca = static_cast<char>(ca - 'A' + 'a');
        if (cb >= 'A' && cb <= 'Z') cb = static_cast<char>(cb - 'A' + 'a');
        if (ca != cb) return false;
    }
    return *a == *b;
}

/// True when `needle` occurs anywhere in `haystack`, comparing ASCII case-
/// insensitively. An empty or null `needle` matches everything (the caller
/// treats that as "no filter"); a null `haystack` never matches.
inline bool icontains(const char *haystack, const char *needle) {
    if (needle == nullptr || needle[0] == '\0') return true;
    if (haystack == nullptr) return false;

    const auto lower = [](char c) -> char {
        return (c >= 'A' && c <= 'Z') ? static_cast<char>(c - 'A' + 'a') : c;
    };

    for (const char *start = haystack; *start != '\0'; ++start) {
        const char *h = start;
        const char *n = needle;
        while (*h != '\0' && *n != '\0' && lower(*h) == lower(*n)) {
            ++h;
            ++n;
        }
        if (*n == '\0') return true;
    }
    return false;
}

/// Lowercase, underscore-separated identifier for `src`, written to `out` and
/// NUL-terminated; returns the length. Used to turn a user-facing card title
/// ("Reduced Motion") into a stable settings-file key ("reduced_motion"), so
/// the file stays readable and does not depend on array indices. Runs of
/// non-alphanumerics collapse to one underscore, and separators at either end
/// are dropped, so re-wording a title does not churn the file.
inline int slugify(const char *src, char *out, int cap) {
    if (out == nullptr || cap <= 0) return 0;

    int  n   = 0;
    bool sep = false; // a separator is owed but not yet emitted
    const auto push = [&n, cap, out](char c) {
        if (n + 1 >= cap) return false; // room for c and the terminator
        out[n++] = c;
        return true;
    };

    for (const char *p = src; p != nullptr && *p != '\0'; ++p) {
        char c = *p;
        if (c >= 'A' && c <= 'Z') c = static_cast<char>(c - 'A' + 'a');
        if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
            if (n > 0) sep = true; // leading junk never emits a separator
            continue;
        }
        if (sep && !push('_')) break;
        if (!push(c)) break;
        sep = false;
    }

    out[n] = '\0';
    return n;
}

} // namespace woke::text
