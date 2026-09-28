// ============================================================================
//  woke.wtf — tests/unit/test_text.cpp
//
//  src/utils/string_utils.h. These helpers are load-bearing: `slugify` decides
//  the identity of a card inside the settings file, so a change in its output
//  silently reinterprets every existing file. That is exactly the property a
//  test should pin down.
// ============================================================================

#include <cstring>

#include "test_util.h"

#include "utils/string_utils.h"

using woke::text::copy_truncated;
using woke::text::icontains;
using woke::text::iequals;
using woke::text::slugify;

void run_text_tests() {
    // -- iequals -------------------------------------------------------------
    WOKE_SUITE("text::iequals");
    WOKE_CHECK(iequals("abc", "abc"));
    WOKE_CHECK(iequals("abc", "ABC"));
    WOKE_CHECK(iequals("TrUe", "tRuE"));
    WOKE_CHECK(iequals("", ""));
    WOKE_CHECK_FALSE(iequals("abc", "abd"));
    WOKE_CHECK_FALSE(iequals("abc", "ab"));   // prefix must not match
    WOKE_CHECK_FALSE(iequals("ab", "abc"));   // ... in either direction
    WOKE_CHECK_FALSE(iequals("abc", "abcd")); // ... including length-only
    // Nulls are only equal to each other; a null is never "close enough".
    WOKE_CHECK(iequals(nullptr, nullptr));
    WOKE_CHECK_FALSE(iequals(nullptr, "a"));
    WOKE_CHECK_FALSE(iequals("a", nullptr));

    // -- icontains -----------------------------------------------------------
    WOKE_SUITE("text::icontains");
    WOKE_CHECK(icontains("Reduced Motion", "motion"));
    WOKE_CHECK(icontains("Reduced Motion", "MOTION"));
    WOKE_CHECK(icontains("Reduced Motion", "duced Mo"));
    WOKE_CHECK(icontains("anything", ""));
    WOKE_CHECK(icontains("anything", nullptr));
    WOKE_CHECK(icontains("", ""));            // empty needle matches empty text
    WOKE_CHECK_FALSE(icontains("Reduced Motion", "zzz"));
    WOKE_CHECK_FALSE(icontains("Reduced Motion", "Motionz"));
    WOKE_CHECK_FALSE(icontains("abc", "abcd")); // needle longer than haystack
    WOKE_CHECK_FALSE(icontains("", "abc"));
    WOKE_CHECK_FALSE(icontains(nullptr, "abc"));
    // A failed match must not run off the end of the haystack while comparing
    // a longer needle (the inner loop is bounded by both strings).
    WOKE_CHECK_FALSE(icontains("aa", "aaa"));

    // -- slugify -------------------------------------------------------------
    WOKE_SUITE("text::slugify");
    char buf[64];

    WOKE_CHECK_INT(slugify("Reduced Motion", buf, static_cast<int>(sizeof(buf))), 14);
    WOKE_CHECK_STR(buf, "reduced_motion");

    // The exact titles the ClickGui ships — these strings are what an existing
    // settings file already contains, so they are a compatibility contract.
    WOKE_CHECK_INT(slugify("FPS Counter", buf, static_cast<int>(sizeof(buf))), 11);
    WOKE_CHECK_STR(buf, "fps_counter");
    WOKE_CHECK_INT(slugify("Config Autosave", buf, static_cast<int>(sizeof(buf))), 15);
    WOKE_CHECK_STR(buf, "config_autosave");
    WOKE_CHECK_INT(slugify("Log Toasts", buf, static_cast<int>(sizeof(buf))), 10);
    WOKE_CHECK_STR(buf, "log_toasts");
    WOKE_CHECK_INT(slugify("Card Shadows", buf, static_cast<int>(sizeof(buf))), 12);
    WOKE_CHECK_STR(buf, "card_shadows");

    // Separator runs collapse to one underscore, and both ends are stripped,
    // so re-wording a title does not churn the file.
    slugify("  --Hello,   World!--  ", buf, static_cast<int>(sizeof(buf)));
    WOKE_CHECK_STR(buf, "hello_world");
    slugify("Auto Layout", buf, static_cast<int>(sizeof(buf)));
    WOKE_CHECK_STR(buf, "auto_layout");
    slugify("Clock", buf, static_cast<int>(sizeof(buf)));
    WOKE_CHECK_STR(buf, "clock");

    slugify("21.11", buf, static_cast<int>(sizeof(buf)));
    WOKE_CHECK_STR(buf, "21_11");

    // Degenerate inputs, all of which occur on the path from a settings file.
    WOKE_CHECK_INT(slugify("", buf, static_cast<int>(sizeof(buf))), 0);
    WOKE_CHECK_STR(buf, "");
    WOKE_CHECK_INT(slugify("!!!", buf, static_cast<int>(sizeof(buf))), 0);
    WOKE_CHECK_STR(buf, "");
    WOKE_CHECK_INT(slugify(nullptr, buf, static_cast<int>(sizeof(buf))), 0);
    WOKE_CHECK_INT(slugify("abc", nullptr, 8), 0);
    WOKE_CHECK_INT(slugify("abc", buf, 0), 0);
    WOKE_CHECK_INT(slugify("abc", buf, -1), 0);

    // Truncation: a small buffer must yield a NUL-terminated prefix and a
    // matching return length, never an overflow.
    char small[6];
    WOKE_CHECK_INT(slugify("abcdefgh", small, static_cast<int>(sizeof(small))), 5);
    WOKE_CHECK_STR(small, "abcde");
    WOKE_CHECK_INT(slugify("abc", small, 4), 3);
    WOKE_CHECK_STR(small, "abc");
    // cap == 1 leaves room for the terminator only.
    char one[1];
    WOKE_CHECK_INT(slugify("abc", one, 1), 0);
    WOKE_CHECK_STR(one, "");

    // -- copy_truncated ------------------------------------------------------
    // The fixed-width field copy behind SettingsStore and the animation
    // channel keys. Its whole point is that an over-long source is truncated
    // silently rather than tripping -Wstringop-truncation at -O2+.
    WOKE_SUITE("text::copy_truncated");
    char field[8];

    std::memset(field, 'X', sizeof(field)); // prove the tail is overwritten
    copy_truncated(field, sizeof(field), "hi");
    WOKE_CHECK_STR(field, "hi");

    // Exact fit: cap - 1 bytes plus the terminator.
    copy_truncated(field, sizeof(field), "1234567");
    WOKE_CHECK_STR(field, "1234567");

    // One byte too many: a NUL-terminated prefix, never an overflow.
    copy_truncated(field, sizeof(field), "12345678");
    WOKE_CHECK_STR(field, "1234567");
    copy_truncated(field, sizeof(field),
                   "a string far longer than the field it is stored in");
    WOKE_CHECK_STR(field, "a strin");

    copy_truncated(field, sizeof(field), "");
    WOKE_CHECK_STR(field, "");
    copy_truncated(field, sizeof(field), nullptr);
    WOKE_CHECK_STR(field, "");

    // Degenerate arguments are no-ops, not crashes.
    copy_truncated(nullptr, 8, "x");
    copy_truncated(field, 0, "x");
    copy_truncated(field, 1, "x");
    WOKE_CHECK_STR(field, "");
}
