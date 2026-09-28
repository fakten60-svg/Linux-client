// ============================================================================
//  woke.wtf — src/ui/settings.h
//
//  Settings persistence: a flat, allocation-free key/value store that reads
//  and writes a plain-text `key = value` file.
//
//  Design decisions:
//   * Plain text with one entry per line (not JSON): the file is meant to be
//     hand-edited and diffed, and the UI writes it on every change. A parser
//     for a line-oriented format is ~60 lines; pulling nlohmann/json's writer
//     into the render path for 17 booleans would be all cost and no benefit.
//   * Fixed capacity, no heap: the store is a member of ClickGui, which lives
//     for the whole session, so the render loop stays allocation-free.
//   * Unknown keys survive a load/save round-trip. A file written by a newer
//     build keeps its extra settings instead of losing them on the next write.
//   * No escaping beyond trimming: values are slugs, integers and booleans, so
//     the file needs no quoting rules.
// ============================================================================

#pragma once

#include <cstddef>

namespace woke::ui {

class SettingsStore {
public:
    static constexpr int    kMaxEntries = 96;
    static constexpr size_t kKeyLen     = 48;
    static constexpr size_t kValueLen   = 96;

    struct Entry {
        char key[kKeyLen]     = {};
        char value[kValueLen] = {};
    };

    void clear();

    /// Insert or overwrite. Silently truncates to the fixed field widths;
    /// returns false when the key is empty or the store is full (a full store
    /// is a bug, not a user error, so callers may ignore the result).
    bool set(const char *key, const char *value);
    bool set_bool(const char *key, bool value);

    /// Null when `key` is absent, so callers can distinguish "unset" from any
    /// particular value and fall back to a default.
    const char *get(const char *key) const;
    bool get_bool(const char *key, bool fallback) const;

    int          count() const { return count_; }
    const Entry &entry(int i) const { return entries_[i]; }

    /// Parse `key = value` lines; '#' starts a comment, blank lines are
    /// skipped, whitespace around the key and value is trimmed, and a repeated
    /// key takes its last value. A missing or unreadable file returns false
    /// and leaves the store empty — that is the first-run case, not an error.
    bool load(const char *path);

    /// Write every entry, creating the parent directory when the path has one.
    /// Returns false when the path is empty or any I/O step fails.
    bool save(const char *path) const;

private:
    Entry entries_[kMaxEntries];
    int   count_ = 0;

    int find(const char *key) const;
};

} // namespace woke::ui
