// ============================================================================
//  woke.wtf — tests/unit/main.cpp
//
//  Entry point for the unit suite. Covers the pure/headless layers only:
//  text helpers, animation math, the settings store and the animation
//  controller. Everything that needs a GL context (components, chrome,
//  toasts) is verified by the woketool harness instead — a unit test that
//  needs a window and a GPU is a worse test and a flakier CI job.
//
//  Exit code is the contract: 0 = every check passed, 1 = at least one failed,
//  so `ctest` and CI need no output parsing.
// ============================================================================

#include <cstdio>

#include "test_util.h"

// Defined in the suite translation units (see CMakeLists target woke_unit_tests).
void run_text_tests();
void run_math_tests();
void run_animation_tests();
void run_settings_tests();

int main() {
    std::printf("woke.wtf — unit tests\n"
                "=====================\n");

    run_text_tests();
    run_math_tests();
    run_animation_tests();
    run_settings_tests();

    const woketest::Context &ctx = woketest::context();
    std::printf("\n=====================\n");
    if (ctx.failures == 0) {
        std::printf("OK — %d checks, 0 failures\n", ctx.checks);
        return 0;
    }
    std::printf("FAILED — %d checks, %d failures\n", ctx.checks, ctx.failures);
    return 1;
}
