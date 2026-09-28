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

} // namespace woke::text
