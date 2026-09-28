// ============================================================================
//  woke.wtf — tests/unit/test_math.cpp
//
//  src/utils/math_utils.h. Every UI animation in the project is built out of
//  these six functions, so the properties that matter are the ones the rest of
//  the UI silently relies on: clamping at both ends, symmetry, and
//  frame-rate independence.
// ============================================================================

#include "test_util.h"

#include "utils/math_utils.h"

using namespace woke::math;

namespace {
constexpr float kEps = 1e-5f;
}

void run_math_tests() {
    // -- lerp ----------------------------------------------------------------
    WOKE_SUITE("math::lerp");
    WOKE_CHECK_NEAR(lerp(0.0f, 10.0f, 0.0f), 0.0, kEps);
    WOKE_CHECK_NEAR(lerp(0.0f, 10.0f, 0.5f), 5.0, kEps);
    WOKE_CHECK_NEAR(lerp(0.0f, 10.0f, 1.0f), 10.0, kEps);
    WOKE_CHECK_NEAR(lerp(4.0f, 4.0f, 0.5f), 4.0, kEps);
    // Documented as "clamped-safe" for t outside [0,1]: it extrapolates rather
    // than clamping, so the caller must clamp. Pin the actual behaviour.
    WOKE_CHECK_NEAR(lerp(0.0f, 10.0f, 2.0f), 20.0, kEps);
    WOKE_CHECK_NEAR(lerp(0.0f, 10.0f, -1.0f), -10.0, kEps);
    WOKE_CHECK_NEAR(lerp(-5.0f, 5.0f, 0.25f), -2.5, kEps);

    // -- ease_out_cubic ------------------------------------------------------
    WOKE_SUITE("math::ease_out_cubic");
    WOKE_CHECK_NEAR(ease_out_cubic(0.0f), 0.0, kEps);
    WOKE_CHECK_NEAR(ease_out_cubic(1.0f), 1.0, kEps);
    WOKE_CHECK_NEAR(ease_out_cubic(0.5f), 0.875, kEps);
    WOKE_CHECK_NEAR(ease_out_cubic(-3.0f), 0.0, kEps); // clamped at both ends
    WOKE_CHECK_NEAR(ease_out_cubic(7.0f), 1.0, kEps);
    // "Fast start": more than half the distance is covered in the first half.
    WOKE_CHECK(ease_out_cubic(0.5f) > 0.5f);
    // Monotone, so a tween never runs backwards.
    float prev = ease_out_cubic(0.0f);
    for (int i = 1; i <= 100; ++i) {
        const float v = ease_out_cubic(static_cast<float>(i) / 100.0f);
        WOKE_CHECK(v >= prev);
        prev = v;
    }

    // -- ease_in_out_quart ---------------------------------------------------
    WOKE_SUITE("math::ease_in_out_quart");
    WOKE_CHECK_NEAR(ease_in_out_quart(0.0f), 0.0, kEps);
    WOKE_CHECK_NEAR(ease_in_out_quart(1.0f), 1.0, kEps);
    WOKE_CHECK_NEAR(ease_in_out_quart(0.5f), 0.5, kEps);
    WOKE_CHECK_NEAR(ease_in_out_quart(-1.0f), 0.0, kEps);
    WOKE_CHECK_NEAR(ease_in_out_quart(1.5f), 1.0, kEps);
    // Symmetric about the midpoint — this is what makes a retarget read as
    // deliberate rather than lurching.
    for (int i = 0; i <= 50; ++i) {
        const float t = static_cast<float>(i) / 50.0f;
        WOKE_CHECK_NEAR(ease_in_out_quart(t) + ease_in_out_quart(1.0f - t), 1.0,
                        1e-4);
    }
    // Slow start: well under half the distance in the first quarter.
    WOKE_CHECK(ease_in_out_quart(0.25f) < 0.25f);

    // -- spring_damp ---------------------------------------------------------
    WOKE_SUITE("math::spring_damp");
    // dt <= 0 must be a no-op, otherwise a stalled frame would snap values.
    WOKE_CHECK_NEAR(spring_damp(0.2f, 1.0f, 0.1f, 0.0f), 0.2, kEps);
    WOKE_CHECK_NEAR(spring_damp(0.2f, 1.0f, 0.1f, -0.5f), 0.2, kEps);
    WOKE_CHECK_NEAR(spring_damp(0.5f, 0.5f, 0.1f, 0.016f), 0.5, kEps);
    // ~87% of the gap closes in one `smoothing` window.
    const float one_window = spring_damp(0.0f, 1.0f, 0.1f, 0.1f);
    WOKE_CHECK_NEAR(one_window, 1.0 - 1.0 / 2.718281828, 1e-3);
    // No overshoot, ever — hover brightness depends on this.
    float v = 0.0f;
    for (int i = 0; i < 200; ++i) {
        v = spring_damp(v, 1.0f, 0.3f, 0.05f);
        WOKE_CHECK(v <= 1.0f + kEps);
        WOKE_CHECK(v >= -kEps);
    }
    WOKE_CHECK_NEAR(v, 1.0, 1e-3);
    // Frame-rate independence: two half-steps equal one whole step, which is
    // the property that makes the UI feel identical at 60 and 144 Hz.
    const float whole = spring_damp(0.0f, 1.0f, 0.2f, 0.4f);
    const float half  = spring_damp(spring_damp(0.0f, 1.0f, 0.2f, 0.2f),
                                    1.0f, 0.2f, 0.2f);
    WOKE_CHECK_NEAR(half, whole, 1e-5);
    // Descending towards a lower target closes the same fraction of the gap,
    // so 10 -> 0 lands on 10/e rather than on 10 - 10/e.
    WOKE_CHECK_NEAR(spring_damp(10.0f, 0.0f, 0.1f, 0.1f), 10.0 / 2.718281828,
                    1e-3);

    // -- saturate_range ------------------------------------------------------
    WOKE_SUITE("math::saturate_range");
    WOKE_CHECK_NEAR(saturate_range(5.0f, 0.0f, 10.0f), 0.5, kEps);
    WOKE_CHECK_NEAR(saturate_range(0.0f, 0.0f, 10.0f), 0.0, kEps);
    WOKE_CHECK_NEAR(saturate_range(10.0f, 0.0f, 10.0f), 1.0, kEps);
    WOKE_CHECK_NEAR(saturate_range(-1.0f, 0.0f, 10.0f), 0.0, kEps);
    WOKE_CHECK_NEAR(saturate_range(99.0f, 0.0f, 10.0f), 1.0, kEps);
    // Degenerate span: no divide by zero, and it degrades to a step.
    WOKE_CHECK_NEAR(saturate_range(5.0f, 1.0f, 1.0f), 1.0, kEps);
    WOKE_CHECK_NEAR(saturate_range(0.0f, 1.0f, 1.0f), 0.0, kEps);

    // -- channel8 ------------------------------------------------------------
    WOKE_SUITE("math::channel8");
    // Packed 0xAABBGGRR: what every IM_COL32 in theme.h is built from.
    constexpr unsigned int packed = 0x11223344u;
    WOKE_CHECK_INT(channel8(packed, 0), 0x44);
    WOKE_CHECK_INT(channel8(packed, 8), 0x33);
    WOKE_CHECK_INT(channel8(packed, 16), 0x22);
    WOKE_CHECK_INT(channel8(packed, 24), 0x11);
    WOKE_CHECK_INT(channel8(0xFFFF5F56u, 0), 0x56); // light_red's red channel
    WOKE_CHECK_INT(channel8(0x00000000u, 8), 0);
}
