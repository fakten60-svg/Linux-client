// ============================================================================
//  woke.wtf — src/core/logger.h
//
//  Multi-session logging engine.
//
//    * Console: ANSI color-coded output
//        DEBUG = gray   INFO = cyan   WARN = yellow   ERROR = red
//      Colors are enabled when stdout is a TTY, or when WOKE_FORCE_COLOR=1
//      (for piped/test capture). Timestamps: [YYYY-MM-DD HH:MM:SS.mmm] local.
//
//    * Files: every load creates logs/YYYY-MM-DD_HH-MM-SS.log and mirrors
//      the active session into logs/latest.log (overwritten each session).
//
//  Threading: one internal mutex serializes file writes; console writes go
//  through the stdio lock. Hot paths format on the caller's stack only —
//  no heap allocation per line. Error-level lines are flushed immediately;
//  everything else flushes on destruction.
//
//  Lifecycle: init() from the constructor bootstrap; shutdown() from the
//  destructor when the library unloads. If shutdown() was not reached
//  (process exit without unload), destructor-less FILE* handles are closed
//  by the C runtime — safe, since we never hold buffered state that must
//  reach disk beyond shutdown().
// ============================================================================

#pragma once

#include <cstdarg>

namespace woke::log {

enum class Level : int {
    kDebug = 0,
    kInfo  = 1,
    kWarn  = 2,
    kError = 3,
};

/// Bootstrap-time initialization. Creates logs/ relative to the process CWD,
/// opens the session-stamped file + latest.log mirror, and writes a session
/// banner. Safe to call twice (second call is a no-op); returns false when
/// file output could not be opened (console logging still works).
bool init();

/// Flush and close file sinks. Called on library unload.
void shutdown();

/// Whether init() completed successfully (file sinks open).
bool is_active();

/// Core printf-style entry point. `tag` may be empty/null for none.
/// Format is checked at compile time when the compiler supports it.
#if defined(__GNUC__)
void write(Level level, const char *tag, const char *fmt, ...)
    __attribute__((format(printf, 3, 4)));
#else
void write(Level level, const char *tag, const char *fmt, ...);
#endif

// Level conveniences — the full call surface used across the codebase.
void debug(const char *tag, const char *fmt, ...) __attribute__((format(printf, 2, 3)));
void info(const char *tag, const char *fmt, ...)  __attribute__((format(printf, 2, 3)));
void warn(const char *tag, const char *fmt, ...)  __attribute__((format(printf, 2, 3)));
void error(const char *tag, const char *fmt, ...) __attribute__((format(printf, 2, 3)));

} // namespace woke::log
