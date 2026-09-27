// ============================================================================
//  woke.wtf — src/ui/theme.h
//
//  All colors, sizes and timings as constexpr tokens. Single source of truth
//  for the macOS look; render code never hardcodes a hex value.
//
//  Color rationale (each token carries its reason inline):
//   * Window/backdrop values follow the macOS dark-desktop hierarchy:
//     a near-black desaturated blue-gray base (#0B0E14) so the white type
//     carries contrast, with a cool 1px stroke (#2A3548) instead of a hard
//     black outline — macOS separates surfaces with lightness, not lines.
//   * Accent is Apple's system blue/cyan pair from the dark-mode palette;
//     the pill toggle cross-fades Dark Gray -> Apple Blue exactly like
//     NSSwitch on Big Sur+.
//   * Text uses a three-step ramp (white / near-white / muted gray) matching
//     macOS label colors (labelColor, secondaryLabelColor).
// ============================================================================

#pragma once

#include <imgui.h>

namespace woke::theme {

// --- Colors (packed 0xAABBGGRR, ImGui's IM_COL32 layout) --------------------
namespace color {

// Window chrome — macOS dark desktop hierarchy: near-black blue-gray base,
// cool stroke instead of hard black; macOS separates surfaces by lightness.
constexpr ImU32 window_bg      = IM_COL32(0x0B, 0x0E, 0x14, 230); // 90% alpha per spec
constexpr ImU32 window_stroke  = IM_COL32(0x2A, 0x35, 0x48, 255); // cool edge line
constexpr ImU32 sidebar_bg     = IM_COL32(0x10, 0x14, 0x1C, 255); // one step above base
constexpr ImU32 title_bar_text = IM_COL32(0x8A, 0x96, 0xA8, 255); // muted gray per spec

// Cards — "Dark Slate": sits between window base and content so cards read
// as raised surfaces without a border.
constexpr ImU32 card_bg        = IM_COL32(0x1C, 0x22, 0x2D, 255);
constexpr ImU32 card_bg_hover  = IM_COL32(0x23, 0x2A, 0x37, 255);
constexpr ImU32 card_stroke    = IM_COL32(0x2A, 0x35, 0x48, 120); // 1px hairline, half alpha

// Text ramp — matches macOS labelColor / secondaryLabelColor.
constexpr ImU32 text_bright    = IM_COL32(0xF5, 0xF7, 0xFA, 255); // white card titles
constexpr ImU32 text_primary   = IM_COL32(0xE6, 0xEA, 0xF0, 255);
constexpr ImU32 text_muted     = IM_COL32(0x8A, 0x96, 0xA8, 255); // descriptions, keybinds

// Accents — Apple system blue (dark mode) + system cyan.
constexpr ImU32 apple_blue     = IM_COL32(0x0A, 0x84, 0xFF, 255);
constexpr ImU32 apple_cyan     = IM_COL32(0x64, 0xD2, 0xFF, 255);

// Pill toggle — off state is a neutral dark gray (NSSwitch off), on cross-
// fades to Apple Blue/Cyan per spec.
constexpr ImU32 pill_off       = IM_COL32(0x3A, 0x43, 0x50, 255);
constexpr ImU32 knob           = IM_COL32(0xFF, 0xFF, 0xFF, 255);
constexpr ImU32 knob_shadow    = IM_COL32(0x00, 0x00, 0x00, 60);

// Traffic lights — exact spec values.
constexpr ImU32 light_red      = IM_COL32(0xFF, 0x5F, 0x56, 255);
constexpr ImU32 light_yellow   = IM_COL32(0xFF, 0xBD, 0x2E, 255);
constexpr ImU32 light_green    = IM_COL32(0x27, 0xC9, 0x3F, 255);
constexpr ImU32 light_hover_mul  = IM_COL32(0x64, 0x64, 0x64, 0); // marker only, see light_hover()
constexpr ImU32 light_press_mul  = IM_COL32(0x00, 0x00, 0x00, 0); // marker only

// Search field + category highlight.
constexpr ImU32 field_bg       = IM_COL32(0x1A, 0x20, 0x2B, 255);
constexpr ImU32 field_stroke   = IM_COL32(0x2A, 0x35, 0x48, 180);
constexpr ImU32 selection_bg   = IM_COL32(0x0A, 0x84, 0xFF, 36);  // 14% blue wash

// Toasts.
constexpr ImU32 toast_bg       = IM_COL32(0x18, 0x1E, 0x28, 242);
constexpr ImU32 toast_stroke   = IM_COL32(0x2A, 0x35, 0x48, 200);

// Soft shadow base (alpha scaled per layer in render_utils).
constexpr ImU32 shadow         = IM_COL32(0x00, 0x00, 0x00, 0);

} // namespace color

// --- Metrics ----------------------------------------------------------------
namespace metric {
constexpr float window_w          = 720.0f;
constexpr float window_h          = 480.0f;
constexpr float window_rounding   = 14.0f;  // spec: window_rounding = 14.0
constexpr float card_rounding     = 8.0f;   // spec: frame_rounding = 8.0
constexpr float card_padding      = 14.0f;
constexpr float title_bar_h       = 40.0f;
constexpr float sidebar_w         = 176.0f;
constexpr float light_radius      = 6.0f;   // macOS traffic-light diameter ratio
constexpr float light_spacing     = 20.0f;  // 8px radius pitch + gap
constexpr float pill_w            = 36.0f;
constexpr float pill_h            = 20.0f;  // NSSwitch proportions, scaled down
constexpr float pill_knob_inset   = 2.0f;
constexpr float card_h            = 62.0f;
constexpr float card_gap          = 10.0f;
constexpr float search_h          = 28.0f;
constexpr float toast_w           = 300.0f;
constexpr float toast_h           = 52.0f;
constexpr float stroke_w          = 1.0f;   // 1px window stroke per spec
} // namespace metric

// --- Timing (seconds) -------------------------------------------------------
namespace time {
constexpr float hover_click      = 0.120f; // spec: 120ms traffic-light hover
constexpr float knob             = 0.180f; // pill knob retarget (ease_in_out_quart)
constexpr float fade             = 0.150f; // card hover cross-fade
constexpr float window_appear    = 0.220f; // window scale/fade-in (ease_out_cubic)
constexpr float toast_slide      = 0.260f; // toast enter/exit
constexpr float toast_hold       = 3.500f; // visible duration before exit slide
} // namespace time

/// Hover = brightness-lerp up, press = darken 15% (spec). Computed from the
/// base color so every light shares one rule — no per-color constants.
constexpr ImU32 light_hover(ImU32 base) {
    // brightness-lerp: mix toward white by 18%.
    auto ch = [base](int shift) {
        const int v = static_cast<int>((base >> shift) & 0xFF);
        const int mixed = v + static_cast<int>((255 - v) * 0.18f);
        return static_cast<unsigned>(mixed > 255 ? 255 : mixed);
    };
    return IM_COL32(ch(0), ch(8), ch(16), 255);
}

constexpr ImU32 light_press(ImU32 base) {
    // press = darken 15% per spec.
    auto ch = [base](int shift) {
        const int v = static_cast<int>((base >> shift) & 0xFF);
        return static_cast<unsigned>(v * 85 / 100);
    };
    return IM_COL32(ch(0), ch(8), ch(16), 255);
}

/// Linear blend of two packed colors, per channel (alpha included).
constexpr ImU32 blend(ImU32 a, ImU32 b, float t) {
    if (t < 0.0f) t = 0.0f;
    if (t > 1.0f) t = 1.0f;
    auto ch = [t](ImU32 c, int shift) {
        const int v = static_cast<int>((c >> shift) & 0xFF);
        return static_cast<unsigned>(v);
    };
    const auto mix = [t](unsigned x, unsigned y) {
        return static_cast<unsigned>(static_cast<int>(x) +
            static_cast<int>((static_cast<float>(y) - static_cast<float>(x)) * t));
    };
    return IM_COL32(mix(ch(a, 0), ch(b, 0)), mix(ch(a, 8), ch(b, 8)),
                    mix(ch(a, 16), ch(b, 16)), mix(ch(a, 24), ch(b, 24)));
}

} // namespace woke::theme
