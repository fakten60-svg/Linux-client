// ============================================================================
//  woke.wtf — src/utils/math_utils.h
//
//  Pure, stateless math primitives for UI animation. Header-only so the
//  optimizer inlines every call site; -fno-rtti compliant (no typeid, no
//  dynamic_cast). All functions are allocation-free and branch-light.
//
//  Rationale for the easing set: these two curves are the Apple-standard
//  pair used by macOS sheet/window/selection animations (ease-out for
//  entrances, ease-in-out for toggles/retargets). spring_damp mirrors the
//  frame-rate-independent critically-damped smoothing Apple uses for
//  continuous properties (knob positions, hover brightness) because it
//  converges without overshoot and is stable at any dt.
// ============================================================================

#pragma once

#include <cmath>

namespace woke::math {

/// Linear interpolation, clamped-safe for t outside [0,1].
constexpr float lerp(float a, float b, float t) noexcept {
    return a + (b - a) * t;
}

/// Apple-standard ease-out: fast start, gentle settle. Used for entrances
/// (window appear, card slide-in, toggle knob retarget).
constexpr float ease_out_cubic(float t) noexcept {
    if (t < 0.0f) t = 0.0f;
    if (t > 1.0f) t = 1.0f;
    const float inv = 1.0f - t;
    return 1.0f - inv * inv * inv;
}

/// Apple-standard symmetric ease: slow-fast-slow. Used for continuous
/// state changes (pill knob cross-fade, category highlight) so motion
/// reads as deliberate rather than reactive.
constexpr float ease_in_out_quart(float t) noexcept {
    if (t < 0.0f) t = 0.0f;
    if (t > 1.0f) t = 1.0f;
    if (t < 0.5f) {
        const float x = 2.0f * t;
        return 0.5f * x * x * x * x;
    }
    const float x = 2.0f * (1.0f - t);
    return 1.0f - 0.5f * x * x * x * x;
}

/// Frame-rate-independent critically-damped spring (exponential smoothing
/// with a half-life). `smoothing` is the time in seconds to close ~87% of
/// the remaining distance; smaller = snappier. Stable for any dt > 0.
inline float spring_damp(float current, float target, float smoothing,
                         float dt) noexcept {
    if (dt <= 0.0f) return current;
    const float factor = 1.0f - std::exp(-dt / smoothing);
    return current + (target - current) * factor;
}

/// Remap v from [a,b] to [0,1], clamped. Building block for progress bars
/// and hover detection; keeps call sites readable.
constexpr float saturate_range(float v, float a, float b) noexcept {
    if (b <= a) return v >= b ? 1.0f : 0.0f;
    float t = (v - a) / (b - a);
    if (t < 0.0f) t = 0.0f;
    if (t > 1.0f) t = 1.0f;
    return t;
}

/// Extract an integer channel (0-255) out of a packed 0xAABBGGRR value —
/// ImGui's IM_COL32 layout. Header-only so theme.h can build constants
/// without pulling in imgui.h at every consumer.
constexpr int channel8(unsigned int col, int shift) noexcept {
    return static_cast<int>((col >> shift) & 0xFFu);
}

} // namespace woke::math
