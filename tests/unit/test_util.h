// ============================================================================
//  woke.wtf — tests/unit/test_util.h
//
//  A ~70-line assertion harness. Deliberately not a test framework: the
//  project vendors every dependency it takes on, and pulling GoogleTest into
//  the build to assert a few dozen pure functions would dwarf the code under
//  test. This header is the whole harness.
//
//  Every check reports into one process-wide context, so a suite is just a
//  function that calls the macros and `main` derives the exit code from the
//  failure count. No registration, no fixtures, no heap.
// ============================================================================

#pragma once

#include <cmath>
#include <cstdio>
#include <cstring>

namespace woketest {

struct Context {
    int checks   = 0;
    int failures = 0;
};

inline Context &context() {
    static Context ctx;
    return ctx;
}

/// Print a suite banner. Purely cosmetic — keeps the ctest output readable
/// when a failure needs to be traced back to a file.
inline void suite(const char *name) {
    std::printf("\n[ %s ]\n", name);
}

inline void check(bool ok, const char *expr, const char *file, int line) {
    ++context().checks;
    if (ok) return;
    ++context().failures;
    std::printf("  FAIL %s:%d  %s\n", file, line, expr);
}

inline void check_str(const char *actual, const char *expected,
                      const char *expr, const char *file, int line) {
    ++context().checks;
    // Null is a value here, not an error: the store's accessors use nullptr to
    // mean "absent", and the tests assert on exactly that.
    const bool ok = (actual == nullptr || expected == nullptr)
                        ? actual == expected
                        : std::strcmp(actual, expected) == 0;
    if (ok) return;
    ++context().failures;
    std::printf("  FAIL %s:%d  %s\n    expected \"%s\"\n    actual   \"%s\"\n",
                file, line, expr, expected != nullptr ? expected : "(null)",
                actual != nullptr ? actual : "(null)");
}

inline void check_int(long long actual, long long expected, const char *expr,
                      const char *file, int line) {
    ++context().checks;
    if (actual == expected) return;
    ++context().failures;
    std::printf("  FAIL %s:%d  %s\n    expected %lld\n    actual   %lld\n",
                file, line, expr, expected, actual);
}

inline void check_near(double actual, double expected, double tol,
                       const char *expr, const char *file, int line) {
    ++context().checks;
    if (std::fabs(actual - expected) <= tol) return;
    ++context().failures;
    std::printf("  FAIL %s:%d  %s\n    expected %.6f\n    actual   %.6f\n",
                file, line, expr, expected, actual);
}

} // namespace woketest

#define WOKE_SUITE(name) ::woketest::suite(name)

#define WOKE_CHECK(cond) \
    ::woketest::check((cond), #cond, __FILE__, __LINE__)

#define WOKE_CHECK_FALSE(cond) \
    ::woketest::check(!(cond), "!(" #cond ")", __FILE__, __LINE__)

#define WOKE_CHECK_STR(actual, expected)                            \
    ::woketest::check_str((actual), (expected),                     \
                          #actual " == " #expected, __FILE__, __LINE__)

#define WOKE_CHECK_INT(actual, expected)                            \
    ::woketest::check_int((actual), (expected),                     \
                          #actual " == " #expected, __FILE__, __LINE__)

#define WOKE_CHECK_NEAR(actual, expected, tol)                      \
    ::woketest::check_near((actual), (expected), (tol),             \
                           #actual " ~= " #expected, __FILE__, __LINE__)
