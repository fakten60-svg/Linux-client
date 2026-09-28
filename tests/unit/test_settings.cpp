// ============================================================================
//  woke.wtf — tests/unit/test_settings.cpp
//
//  src/ui/settings.{h,cpp}. This is the one part of the UI that writes to the
//  user's disk and the one part a user is invited to hand-edit, so the parse
//  rules are a contract, not an implementation detail: comments and blank
//  lines are skipped, whitespace is trimmed, the last of a repeated key wins,
//  a missing file means "first run" rather than an error, and a value the
//  parser does not recognise falls back rather than guessing.
//
//  Files are written under /tmp/woke-unit-<pid>/ so a test run cannot touch
//  the user's real settings and two concurrent runs cannot collide.
// ============================================================================

#include <cstdio>
#include <cstring>
#include <sys/stat.h>
#include <unistd.h>

#include "test_util.h"

#include "ui/settings.h"

using woke::ui::SettingsStore;

namespace {

char g_dir[160];      // /tmp/woke-unit-<pid>
bool g_dir_ready = false;

void init_tmp_dir() {
    std::snprintf(g_dir, sizeof(g_dir), "/tmp/woke-unit-%ld",
                  static_cast<long>(::getpid()));
    ::mkdir(g_dir, 0700); // EEXIST from a previous suite run is fine
    struct stat st {};
    g_dir_ready = ::stat(g_dir, &st) == 0 && S_ISDIR(st.st_mode);
}

void temp_path(char *out, size_t cap, const char *name) {
    std::snprintf(out, cap, "%s/%s", g_dir, name);
}

bool write_text(const char *path, const char *text) {
    std::FILE *f = std::fopen(path, "wb");
    if (f == nullptr) return false;
    const bool ok = std::fputs(text, f) >= 0;
    return std::fclose(f) == 0 && ok;
}

/// Read a whole file into `out`; returns false when it cannot be opened.
bool read_text(const char *path, char *out, size_t cap) {
    std::FILE *f = std::fopen(path, "rb");
    if (f == nullptr) return false;
    const size_t n = std::fread(out, 1, cap - 1, f);
    out[n] = '\0';
    std::fclose(f);
    return true;
}

} // namespace

void run_settings_tests() {
    init_tmp_dir();
    WOKE_CHECK(g_dir_ready);
    if (!g_dir_ready) return; // every path below would silently "pass"

    char path[256];

    // -- get / set -----------------------------------------------------------
    WOKE_SUITE("SettingsStore::set/get");
    {
        SettingsStore s;
        WOKE_CHECK_INT(s.count(), 0);
        WOKE_CHECK_STR(s.get("absent"), nullptr);

        WOKE_CHECK(s.set("ui.category", "3"));
        WOKE_CHECK_INT(s.count(), 1);
        WOKE_CHECK_STR(s.get("ui.category"), "3");

        // Overwriting updates in place; a saved file never grows a duplicate.
        WOKE_CHECK(s.set("ui.category", "0"));
        WOKE_CHECK_INT(s.count(), 1);
        WOKE_CHECK_STR(s.get("ui.category"), "0");

        // A null value is an empty string, not a crash.
        WOKE_CHECK(s.set("empty", nullptr));
        WOKE_CHECK_STR(s.get("empty"), "");

        // Rejections: an unnamed entry is a caller bug.
        WOKE_CHECK_FALSE(s.set("", "1"));
        WOKE_CHECK_FALSE(s.set(nullptr, "1"));
        WOKE_CHECK_STR(s.get(""), nullptr);
        WOKE_CHECK_STR(s.get(nullptr), nullptr);

        s.clear();
        WOKE_CHECK_INT(s.count(), 0);
        WOKE_CHECK_STR(s.get("ui.category"), nullptr);
    }

    // -- booleans ------------------------------------------------------------
    WOKE_SUITE("SettingsStore booleans");
    {
        SettingsStore s;
        WOKE_CHECK(s.set_bool("on", true));
        WOKE_CHECK(s.set_bool("off", false));
        WOKE_CHECK_STR(s.get("on"), "1");
        WOKE_CHECK_STR(s.get("off"), "0");
        WOKE_CHECK(s.get_bool("on", false));
        WOKE_CHECK_FALSE(s.get_bool("off", true));

        // A hand-editing user writes words, so the words are accepted in any
        // case, and an unrecognised value falls back instead of guessing.
        const char *spellings[][2] = {
            {"s1", "true"},  {"s2", "TRUE"},  {"s3", "On"},   {"s4", "yes"},
            {"s5", "false"}, {"s6", "OFF"},   {"s7", "No"},   {"s8", "maybe"},
        };
        for (const auto &sp : spellings) s.set(sp[0], sp[1]);
        WOKE_CHECK(s.get_bool("s1", false));
        WOKE_CHECK(s.get_bool("s2", false));
        WOKE_CHECK(s.get_bool("s3", false));
        WOKE_CHECK(s.get_bool("s4", false));
        WOKE_CHECK_FALSE(s.get_bool("s5", true));
        WOKE_CHECK_FALSE(s.get_bool("s6", true));
        WOKE_CHECK_FALSE(s.get_bool("s7", true));
        WOKE_CHECK(s.get_bool("s8", true));   // fallback, both ways
        WOKE_CHECK_FALSE(s.get_bool("s8", false));
        WOKE_CHECK(s.get_bool("missing", true));
        WOKE_CHECK_FALSE(s.get_bool("missing", false));
    }

    // -- fixed capacity ------------------------------------------------------
    WOKE_SUITE("SettingsStore capacity");
    {
        SettingsStore s;
        char key[16];
        for (int i = 0; i < SettingsStore::kMaxEntries; ++i) {
            std::snprintf(key, sizeof(key), "c%d", i);
            WOKE_CHECK(s.set(key, "1"));
        }
        WOKE_CHECK_INT(s.count(), SettingsStore::kMaxEntries);

        // A full store refuses new keys...
        WOKE_CHECK_FALSE(s.set("one.too.many", "1"));
        WOKE_CHECK_INT(s.count(), SettingsStore::kMaxEntries);
        // ...but still accepts updates to keys it already holds, so a full
        // file keeps working instead of freezing.
        WOKE_CHECK(s.set("c0", "0"));
        WOKE_CHECK_STR(s.get("c0"), "0");
    }

    // -- truncation ----------------------------------------------------------
    WOKE_SUITE("SettingsStore truncation");
    {
        SettingsStore s;
        char long_key[120];
        std::memset(long_key, 'k', sizeof(long_key) - 1);
        long_key[sizeof(long_key) - 1] = '\0';
        char long_val[220];
        std::memset(long_val, 'v', sizeof(long_val) - 1);
        long_val[sizeof(long_val) - 1] = '\0';

        WOKE_CHECK(s.set(long_key, long_val));
        const char *stored = s.get(long_key);
        WOKE_CHECK(stored != nullptr);
        if (stored != nullptr) {
            // Stored at the fixed field widths, and the same truncation on
            // lookup still finds it.
            WOKE_CHECK_INT(static_cast<long long>(std::strlen(stored)),
                           SettingsStore::kValueLen - 1);
            WOKE_CHECK_INT(static_cast<long long>(std::strlen(s.entry(0).key)),
                           SettingsStore::kKeyLen - 1);
        }
    }

    // -- load ----------------------------------------------------------------
    WOKE_SUITE("SettingsStore::load");
    {
        SettingsStore s;
        temp_path(path, sizeof(path), "load.conf");
        WOKE_CHECK(write_text(path,
            "# a comment\n"
            "\n"
            "   indented   =   spaced value   \n"
            "compact=value\n"
            "no separator here\n"
            "eq = a=b\n"
            "dup = first\n"
            "dup = second\n"
            "   =  empty key  \n"
            "# commented = out\n"
            "windows = crlf\r\n"));
        WOKE_CHECK(s.load(path));

        WOKE_CHECK_STR(s.get("indented"), "spaced value");
        WOKE_CHECK_STR(s.get("compact"), "value");
        WOKE_CHECK_STR(s.get("no separator here"), nullptr);
        WOKE_CHECK_STR(s.get("no"), nullptr);
        // Only the first '=' separates, so a value may contain one.
        WOKE_CHECK_STR(s.get("eq"), "a=b");
        WOKE_CHECK_STR(s.get("dup"), "second"); // last one wins
        WOKE_CHECK_STR(s.get(""), nullptr);     // an empty key is not a key
        WOKE_CHECK_STR(s.get("# commented"), nullptr);
        WOKE_CHECK_STR(s.get("windows"), "crlf"); // \r is trimmed
        WOKE_CHECK_INT(s.count(), 5);
        std::remove(path);

        // Only comments: a valid, empty file.
        SettingsStore empty;
        temp_path(path, sizeof(path), "comments.conf");
        WOKE_CHECK(write_text(path, "# nothing but a comment\n"));
        WOKE_CHECK(empty.load(path));
        WOKE_CHECK_INT(empty.count(), 0);
        std::remove(path);
    }

    WOKE_SUITE("SettingsStore::load failures");
    {
        // A missing file is the first-run case: reported as "nothing loaded",
        // with the defaults the caller already has left untouched.
        SettingsStore s;
        temp_path(path, sizeof(path), "does-not-exist.conf");
        std::remove(path);
        WOKE_CHECK_FALSE(s.load(path));
        WOKE_CHECK_INT(s.count(), 0);

        // Existing entries survive a failed load.
        WOKE_CHECK(s.set("kept", "1"));
        WOKE_CHECK_FALSE(s.load(path));
        WOKE_CHECK_STR(s.get("kept"), "1");

        WOKE_CHECK_FALSE(s.load(nullptr));
        WOKE_CHECK_FALSE(s.load(""));
    }

    // -- save / round-trip ---------------------------------------------------
    WOKE_SUITE("SettingsStore::save");
    {
        SettingsStore src;
        src.set("ui.category", "2");
        src.set_bool("card.reduced_motion", true);
        src.set_bool("card.clock", false);
        src.set("odd value", "kept as-is");

        temp_path(path, sizeof(path), "roundtrip.conf");
        WOKE_CHECK(src.save(path));

        char text[1024];
        WOKE_CHECK(read_text(path, text, sizeof(text)));
        WOKE_CHECK(std::strstr(text, "#") == text); // leads with the header
        WOKE_CHECK(std::strstr(text, "ui.category = 2\n") != nullptr);
        WOKE_CHECK(std::strstr(text, "card.reduced_motion = 1\n") != nullptr);
        WOKE_CHECK(std::strstr(text, "card.clock = 0\n") != nullptr);

        SettingsStore dst;
        WOKE_CHECK(dst.load(path));
        WOKE_CHECK_INT(dst.count(), src.count());
        WOKE_CHECK_STR(dst.get("ui.category"), "2");
        WOKE_CHECK(dst.get_bool("card.reduced_motion", false));
        WOKE_CHECK_FALSE(dst.get_bool("card.clock", true));
        WOKE_CHECK_STR(dst.get("odd value"), "kept as-is");

        // Unknown keys are just entries: a file written by a newer build
        // keeps its extra settings when this build rewrites it.
        SettingsStore newer;
        WOKE_CHECK(newer.load(path));
        WOKE_CHECK(newer.set("from.the.future", "42"));
        WOKE_CHECK(newer.save(path));
        SettingsStore again;
        WOKE_CHECK(again.load(path));
        WOKE_CHECK_STR(again.get("from.the.future"), "42");
        WOKE_CHECK_STR(again.get("ui.category"), "2");
        std::remove(path);

        // No path, no persistence — the default for a caller that never opted
        // in (the screenshot harness relies on this).
        WOKE_CHECK_FALSE(src.save(""));
        WOKE_CHECK_FALSE(src.save(nullptr));

        // An unwritable target reports failure rather than pretending.
        WOKE_CHECK_FALSE(src.save("/dev/null/child.conf"));
    }

    WOKE_SUITE("SettingsStore::save parent directory");
    {
        // "~/.config/woke/woke.conf" needs the last component to exist; the
        // store creates it rather than asking the caller to.
        char nested[192];
        std::snprintf(nested, sizeof(nested), "%s/nested", g_dir);
        ::rmdir(nested); // make sure we are creating it, not finding it

        char nested_file[256];
        std::snprintf(nested_file, sizeof(nested_file), "%s/woke.conf", nested);

        SettingsStore s;
        s.set("a", "1");
        WOKE_CHECK(s.save(nested_file));

        struct stat st {};
        WOKE_CHECK(::stat(nested, &st) == 0 && S_ISDIR(st.st_mode));
        WOKE_CHECK(::stat(nested_file, &st) == 0 && S_ISREG(st.st_mode));

        SettingsStore back;
        WOKE_CHECK(back.load(nested_file));
        WOKE_CHECK_STR(back.get("a"), "1");

        std::remove(nested_file);
        ::rmdir(nested);
    }

    // Leave /tmp as we found it.
    ::rmdir(g_dir);
}
