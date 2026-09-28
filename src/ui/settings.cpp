// ============================================================================
//  woke.wtf — src/ui/settings.cpp
//
//  Line-oriented settings file I/O. Load/save run at startup and on a change
//  only — never inside the render loop — so plain stdio (fgets/fprintf) is
//  used rather than any buffering scheme of our own.
// ============================================================================

#include "ui/settings.h"

#include <cerrno>
#include <cstdio>
#include <cstring>
#include <sys/stat.h>

#include "utils/string_utils.h"

namespace woke::ui {
namespace {

bool is_space(char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
}

/// Trim trailing whitespace in place (the line buffer is ours to edit).
void trim_right(char *s) {
    size_t n = std::strlen(s);
    while (n > 0 && is_space(s[n - 1])) s[--n] = '\0';
}

/// Advance past leading whitespace.
char *trim_left(char *s) {
    while (*s != '\0' && is_space(*s)) ++s;
    return s;
}

/// Best-effort parent-directory creation ("~/.config/woke/woke.conf" needs
/// "woke" to exist). A failure here is not reported: fopen() below is what
/// actually decides whether the file is writable, and it produces the error
/// the caller acts on.
void ensure_parent_dir(const char *path) {
    const char *slash = std::strrchr(path, '/');
    if (slash == nullptr || slash == path) return; // no parent, or "/x"

    char dir[512];
    const size_t n = static_cast<size_t>(slash - path);
    if (n >= sizeof(dir)) return;
    std::memcpy(dir, path, n);
    dir[n] = '\0';

    if (::mkdir(dir, 0755) != 0 && errno != EEXIST) return; // fopen reports it
}

} // namespace

void SettingsStore::clear() {
    for (int i = 0; i < kMaxEntries; ++i) entries_[i] = Entry{};
    count_ = 0;
}

int SettingsStore::find(const char *key) const {
    if (key == nullptr || key[0] == '\0') return -1;
    for (int i = 0; i < count_; ++i)
        if (std::strncmp(entries_[i].key, key, kKeyLen - 1) == 0) return i;
    return -1;
}

bool SettingsStore::set(const char *key, const char *value) {
    if (key == nullptr || key[0] == '\0') return false;

    int i = find(key);
    if (i < 0) {
        if (count_ >= kMaxEntries) return false;
        i = count_++;
    }

    // Truncation is intentional and safe: both buffers are fixed, and the
    // writer emits exactly what is stored, so a truncated value survives a
    // round-trip instead of silently expanding on the next save. See
    // text::copy_truncated for why this is not strncpy().
    text::copy_truncated(entries_[i].key, kKeyLen, key);
    text::copy_truncated(entries_[i].value, kValueLen,
                         value != nullptr ? value : "");
    return true;
}

bool SettingsStore::set_bool(const char *key, bool value) {
    return set(key, value ? "1" : "0");
}

const char *SettingsStore::get(const char *key) const {
    const int i = find(key);
    return i < 0 ? nullptr : entries_[static_cast<size_t>(i)].value;
}

bool SettingsStore::get_bool(const char *key, bool fallback) const {
    const char *v = get(key);
    if (v == nullptr) return fallback;
    // Accept the spellings a hand-editor is likely to write.
    if (v[0] == '0' && v[1] == '\0') return false;
    if (v[0] == '1' && v[1] == '\0') return true;
    if (text::iequals(v, "false") || text::iequals(v, "off") ||
        text::iequals(v, "no"))
        return false;
    if (text::iequals(v, "true") || text::iequals(v, "on") ||
        text::iequals(v, "yes"))
        return true;
    return fallback;
}

bool SettingsStore::load(const char *path) {
    if (path == nullptr || path[0] == '\0') return false;

    std::FILE *f = std::fopen(path, "rb");
    if (f == nullptr) return false; // missing file = first run, not an error

    char line[256];
    while (std::fgets(line, sizeof(line), f) != nullptr) {
        char *s = trim_left(line);
        if (s[0] == '#' || s[0] == '\0') continue;

        char *eq = s;
        while (*eq != '\0' && *eq != '=' && *eq != '\r' && *eq != '\n') ++eq;
        if (*eq != '=') continue; // no separator: not a setting

        *eq = '\0';
        char *value = trim_left(eq + 1);
        trim_right(value);
        trim_right(s);
        if (s[0] != '\0') set(s, value);
    }

    std::fclose(f);
    return true;
}

bool SettingsStore::save(const char *path) const {
    if (path == nullptr || path[0] == '\0') return false;

    ensure_parent_dir(path);

    std::FILE *f = std::fopen(path, "wb");
    if (f == nullptr) return false;

    bool ok = std::fputs("# woke.wtf — UI settings. Written by the client; the\n"
                         "# format is `key = value`, so it is safe to edit by hand.\n",
                         f) >= 0;
    for (int i = 0; i < count_ && ok; ++i)
        ok = std::fprintf(f, "%s = %s\n", entries_[i].key, entries_[i].value) > 0;

    // fclose flushes; a failure there means the data never reached the disk,
    // so it must be reported rather than swallowed.
    if (std::fclose(f) != 0) ok = false;
    return ok;
}

} // namespace woke::ui
