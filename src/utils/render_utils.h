// ============================================================================
//  woke.wtf — src/utils/render_utils.h
//
//  Stateless DrawList helpers. Every function takes an explicit ImDrawList*
//  (never touches ImGui global state except the ambient alpha below, never
//  allocates, never retains) so they compose freely inside any window or child
//  region. These exist because ImGui's stock widget set cannot express the
//  macOS chrome — soft 3-layer shadows, hairline strokes, gradient pills — and
//  stock ChildWindow borders would read as Windows-95 next to it.
//
//  AMBIENT ALPHA: a drawing color handed straight to ImDrawList is literal.
//  ImGuiStyleVar_Alpha only reaches colors resolved through GetColorU32, i.e.
//  stock widgets — so a fully custom-drawn layer cannot be faded with a style
//  var at all. These helpers therefore apply one shared ambient factor, which
//  lets the ClickGUI fade its whole chrome (window, sidebar, cards, text) as a
//  single layer while it animates in and out. At rest it is 1.0 and every
//  color passes through bit-exact.
// ============================================================================

#pragma once

#include <imgui.h>

namespace woke::render {

/// Scale a packed color's alpha by `a` (0..1). Exact at a == 1.0.
constexpr ImU32 fade(ImU32 col, float a) {
    if (a >= 1.0f) return col;
    if (a <= 0.0f) return col & ~IM_COL32_A_MASK;
    const ImU32 src = (col >> IM_COL32_A_SHIFT) & 0xFFu;
    const ImU32 out = static_cast<ImU32>(static_cast<float>(src) * a + 0.5f);
    return (col & ~IM_COL32_A_MASK) | (out << IM_COL32_A_SHIFT);
}

/// Ambient alpha applied by every helper below. One writer per frame.
void  set_ambient_alpha(float a);
float ambient_alpha();

/// Single text run and single line segment; ambient-aware counterparts of the
/// raw ImDrawList calls, which are not.
void text(ImDrawList *dl, ImVec2 pos, ImU32 color, const char *str);
void line(ImDrawList *dl, ImVec2 a, ImVec2 b, ImU32 color,
          float thickness = 1.0f);

/// Rounded rect with an optional 1px hairline stroke (macOS separates by
/// lightness, so the stroke is a cool tint, not black).
void rounded_rect(ImDrawList *dl, ImVec2 min, ImVec2 max, ImU32 fill,
                  ImU32 stroke, float rounding, float stroke_w = 1.0f);

/// 3-layer soft shadow: three expanded, progressively fainter rounded rects
/// under the shape. Layering reads as ambient+key shadow (macOS window
/// style) where a single blurred rect would read as a glow.
void soft_shadow(ImDrawList *dl, ImVec2 min, ImVec2 max, float rounding,
                 float spread = 14.0f, ImU32 base_alpha = 90);

/// Vertical gradient fill rounded rect (top_color -> bottom_color). Used by
/// the pill toggle's on-state and card header sheen.
void gradient_fill(ImDrawList *dl, ImVec2 min, ImVec2 max, ImU32 top,
                   ImU32 bottom, float rounding);

/// Circle with optional stroke (traffic lights, search icon).
void circle(ImDrawList *dl, ImVec2 center, float radius, ImU32 fill,
            ImU32 stroke = 0, float stroke_w = 1.0f);

/// Text clipped to `max_w` with a trailing ellipsis when truncated. Returns
/// the width actually used. macOS labels never overflow hard.
float text_clipped(ImDrawList *dl, ImVec2 pos, float max_w, const char *text,
                   ImU32 color);

} // namespace woke::render
