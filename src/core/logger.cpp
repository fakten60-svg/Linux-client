// ============================================================================
//  woke.wtf — src/core/logger.cpp
//  See logger.h for the full design contract.
// ============================================================================

#include "core/logger.h"

#include <sys/stat.h>
#include <sys/time.h>
#include <unistd.h>

#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>

namespace woke::log {
namespace {

constexpr size_t kMsgBufSize = 1024;   // formatted message cap (stack)
constexpr size_t kLineBufSize = 1400;  // timestamp + level + tag + message

// ANSI SGR sequences. Console line layout:
//   [ts] LEVEL [tag] message        (level colored, tag dim, message plain)
constexpr const char *kReset  = "\033[0m";
constexpr const char *kDim    = "\033[2m";
constexpr const char *kGray   = "\033[90m";  // DEBUG
constexpr const char *kCyan   = "\033[36m";  // INFO
constexpr const char *kYellow = "\033[33m";  // WARN
constexpr const char *kRed    = "\033[31m";  // ERROR

const char *level_name(Level lvl) {
    switch (lvl) {
        case Level::kDebug: return "DEBUG";
        case Level::kInfo:  return "INFO ";
        case Level::kWarn:  return "WARN ";
        case Level::kError: return "ERROR";
    }
    return "?????";
}

const char *level_color(Level lvl) {
    switch (lvl) {
        case Level::kDebug: return kGray;
        case Level::kInfo:  return kCyan;
        case Level::kWarn:  return kYellow;
        case Level::kError: return kRed;
    }
    return kReset;
}

struct State {
    FILE *session_file = nullptr;  // logs/YYYY-MM-DD_HH-MM-SS.log
    FILE *latest_file  = nullptr;  // logs/latest.log (mirrors session_file)
    bool  color_console = false;
    bool  active = false;
    std::mutex mtx;
};

State g_state;

bool dir_exists(const char *path) {
    struct stat st{};
    return ::stat(path, &st) == 0 && S_ISDIR(st.st_mode);
}

bool detect_color_console() {
    // Explicit override wins; otherwise color only a real TTY.
    const char *forced = ::getenv("WOKE_FORCE_COLOR");
    if (forced != nullptr && forced[0] == '1') {
        return true;
    }
    const char *no_color = ::getenv("NO_COLOR");
    if (no_color != nullptr && no_color[0] != '\0') {
        return false;
    }
    return ::isatty(::fileno(stdout)) != 0;
}

/// Local wall-clock broken into fields (gettimeofday + localtime_r).
struct LocalTime {
    int year, mon, day, hour, min, sec, msec;
};

void local_time_now(LocalTime *out) {
    struct timeval tv{};
    ::gettimeofday(&tv, nullptr);

    struct tm local{};
    ::localtime_r(&tv.tv_sec, &local);  // thread-safe local timezone

    out->year = local.tm_year + 1900;
    out->mon = local.tm_mon + 1;
    out->day = local.tm_mday;
    out->hour = local.tm_hour;
    out->min = local.tm_min;
    out->sec = local.tm_sec;
    out->msec = static_cast<int>(tv.tv_usec / 1000);
}

/// Local-timezone timestamp: [YYYY-MM-DD HH:MM:SS.mmm]
void format_timestamp(char *out, size_t cap) {
    LocalTime t;
    local_time_now(&t);
    ::snprintf(out, cap, "[%04d-%02d-%02d %02d:%02d:%02d.%03d]",
               t.year, t.mon, t.day, t.hour, t.min, t.sec, t.msec);
}

/// Prepend a dim marker in console mode (ANSI), nothing for file sinks.
void format_line(char *dst, size_t cap, bool color, Level lvl,
                 const char *tag, const char *msg) {
    char ts[40];
    format_timestamp(ts, sizeof(ts));

    if (color) {
        if (tag != nullptr && tag[0] != '\0') {
            ::snprintf(dst, cap, "%s%s %s%s%s %s[%s]%s %s",
                       kDim, ts, kReset,
                       level_color(lvl), level_name(lvl), kReset,
                       tag, kReset, msg);
        } else {
            // dim(ts) reset color(name) reset " " msg
            ::snprintf(dst, cap, "%s%s %s%s%s%s %s",
                       kDim, ts, kReset, level_color(lvl), level_name(lvl),
                       kReset, msg);
        }
    } else {
        if (tag != nullptr && tag[0] != '\0') {
            ::snprintf(dst, cap, "%s %s [%s] %s", ts, level_name(lvl), tag, msg);
        } else {
            ::snprintf(dst, cap, "%s %s %s", ts, level_name(lvl), msg);
        }
    }
}

void write_file_sinks(const char *line) {
    if (g_state.session_file != nullptr) {
        std::fputs(line, g_state.session_file);
        std::fputc('\n', g_state.session_file);
    }
    if (g_state.latest_file != nullptr) {
        std::fputs(line, g_state.latest_file);
        std::fputc('\n', g_state.latest_file);
    }
}

void write_console(const char *line, Level lvl) {
    std::fputs(line, stdout);
    std::fputc('\n', stdout);
    if (lvl == Level::kError) {
        std::fflush(stdout);  // errors must not sit in the buffer
    }
}

} // namespace

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

bool init() {
    std::lock_guard<std::mutex> lock(g_state.mtx);
    if (g_state.active) {
        return true;  // idempotent
    }

    g_state.color_console = detect_color_console();

    if (!dir_exists("logs") && ::mkdir("logs", 0755) != 0) {
        // Console-only mode: never fatal for the host process.
        g_state.active = true;
        return false;
    }

    LocalTime t;
    local_time_now(&t);
    // Filesystem-safe stamp: 2026-09-27_15-41-00 (dashes, no colons)
    char fname[64];
    ::snprintf(fname, sizeof(fname), "logs/%04d-%02d-%02d_%02d-%02d-%02d.log",
               t.year, t.mon, t.day, t.hour, t.min, t.sec);

    g_state.session_file = std::fopen(fname, "w");
    g_state.latest_file  = std::fopen("logs/latest.log", "w");
    g_state.active = true;

    const bool ok = g_state.session_file != nullptr && g_state.latest_file != nullptr;
    return ok;
}

void shutdown() {
    std::lock_guard<std::mutex> lock(g_state.mtx);
    if (g_state.session_file != nullptr) {
        std::fflush(g_state.session_file);
        std::fclose(g_state.session_file);
        g_state.session_file = nullptr;
    }
    if (g_state.latest_file != nullptr) {
        std::fflush(g_state.latest_file);
        std::fclose(g_state.latest_file);
        g_state.latest_file = nullptr;
    }
    g_state.active = false;
}

bool is_active() {
    return g_state.active;
}

void write(Level lvl, const char *tag, const char *fmt, ...) {
    char msg[kMsgBufSize];

    va_list args;
    va_start(args, fmt);
    const int n = ::vsnprintf(msg, sizeof(msg), fmt, args);
    va_end(args);
    if (n < 0) {
        return;  // malformed format — drop silently, never crash the host
    }

    // Files always receive the plain line; the console receives the colored
    // variant when enabled. Two stack buffers, zero heap.
    char plain[kLineBufSize];
    format_line(plain, sizeof(plain), false, lvl, tag, msg);

    std::lock_guard<std::mutex> lock(g_state.mtx);
    if (g_state.color_console) {
        char colored[kLineBufSize];
        format_line(colored, sizeof(colored), true, lvl, tag, msg);
        write_console(colored, lvl);
    } else {
        write_console(plain, lvl);
    }
    write_file_sinks(plain);
    if (g_state.session_file != nullptr) {
        std::fflush(g_state.session_file);   // crash-safe: keep files current
    }
    if (g_state.latest_file != nullptr) {
        std::fflush(g_state.latest_file);
    }
}

void debug(const char *tag, const char *fmt, ...) {
    char msg[kMsgBufSize];
    va_list args;
    va_start(args, fmt);
    ::vsnprintf(msg, sizeof(msg), fmt, args);
    va_end(args);
    write(Level::kDebug, tag, "%s", msg);
}

void info(const char *tag, const char *fmt, ...) {
    char msg[kMsgBufSize];
    va_list args;
    va_start(args, fmt);
    ::vsnprintf(msg, sizeof(msg), fmt, args);
    va_end(args);
    write(Level::kInfo, tag, "%s", msg);
}

void warn(const char *tag, const char *fmt, ...) {
    char msg[kMsgBufSize];
    va_list args;
    va_start(args, fmt);
    ::vsnprintf(msg, sizeof(msg), fmt, args);
    va_end(args);
    write(Level::kWarn, tag, "%s", msg);
}

void error(const char *tag, const char *fmt, ...) {
    char msg[kMsgBufSize];
    va_list args;
    va_start(args, fmt);
    ::vsnprintf(msg, sizeof(msg), fmt, args);
    va_end(args);
    write(Level::kError, tag, "%s", msg);
}

} // namespace woke::log
