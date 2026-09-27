// ============================================================================
//  woke.wtf — src/utils/render_utils.cpp
// ============================================================================

#include "utils/render_utils.h"

#include <cmath>

#include "ui/theme.h"

namespace woke::render {

void rounded_rect(ImDrawList *dl, ImVec2 min, ImVec2 max, ImU32 fill,
                  ImU32 stroke, float rounding, float stroke_w) {
    if ((fill & IM_COL32_A_MASK) != 0)
        dl->AddRectFilled(min, max, fill, rounding);
    if ((stroke & IM_COL32_A_MASK) != 0 && stroke_w > 0.0f)
        dl->AddRect(min, max, stroke, rounding, 0, stroke_w);
}

void soft_shadow(ImDrawList *dl, ImVec2 min, ImVec2 max, float rounding,
                 float spread, ImU32 base_alpha) {
    // Three layers: wide/faint (ambient), mid, tight/dense (key). Alphas
    // follow a 1 : 2 : 4 falloff so the union reads as one soft penumbra
    // rather than three visible rings.
    struct Layer { float grow; float alpha_scale; };
    constexpr Layer kLayers[3] = { { 1.00f, 1.0f }, { 0.62f, 2.0f }, { 0.30f, 4.0f } };

    for (const Layer &l : kLayers) {
        const float g   = spread * l.grow;
        const int   a   = static_cast<int>(static_cast<float>(base_alpha) *
                                           l.alpha_scale / 7.0f);
        if (a <= 0) continue;
        const ImU32 col = IM_COL32(0, 0, 0, a);
        dl->AddRectFilled(ImVec2(min.x - g, min.y - g + 2.0f),
                          ImVec2(max.x + g, max.y + g + 4.0f),
                          col, rounding + g * 0.6f);
    }
}

void gradient_fill(ImDrawList *dl, ImVec2 min, ImVec2 max, ImU32 top,
                   ImU32 bottom, float rounding) {
    dl->AddRectFilledMultiColor(min, max, top, top, bottom, bottom);
    // AddRectFilledMultiColor cannot round; cover the corners with four
    // small fill rects matching the top/bottom colors — visually identical
    // to a rounded gradient at these radii and allocation-free.
    const float r = rounding;
    if (r <= 0.0f) return;
    const ImU32 edge[2] = { top, bottom };
    const float mid_y = (min.y + max.y) * 0.5f;
    // top corners (top color), bottom corners (bottom color)
    dl->AddRectFilled(ImVec2(min.x, min.y), ImVec2(min.x + r, min.y + r), edge[0]);
    dl->AddRectFilled(ImVec2(max.x - r, min.y), ImVec2(max.x, min.y + r), edge[0]);
    dl->AddRectFilled(ImVec2(min.x, max.y - r), ImVec2(min.x + r, max.y), edge[1]);
    dl->AddRectFilled(ImVec2(max.x - r, max.y - r), ImVec2(max.x, max.y), edge[1]);
    (void)mid_y;
}

void circle(ImDrawList *dl, ImVec2 center, float radius, ImU32 fill,
            ImU32 stroke, float stroke_w) {
    if ((fill & IM_COL32_A_MASK) != 0)
        dl->AddCircleFilled(center, radius, fill);
    if ((stroke & IM_COL32_A_MASK) != 0 && stroke_w > 0.0f)
        dl->AddCircle(center, radius, stroke, 0, stroke_w);
}

float text_clipped(ImDrawList *dl, ImVec2 pos, float max_w, const char *text,
                   ImU32 color) {
    if (text == nullptr || text[0] == '\0') return 0.0f;
    ImFont *font = ImGui::GetFont();
    const float font_h = ImGui::GetTextLineHeight();
    const float full_w = font->CalcTextSizeA(font_h, 3.402823466e+38F, 0.0f, text).x;

    if (full_w <= max_w) {
        dl->AddText(pos, color, text);
        return full_w;
    }

    // Binary-search-free approach: ellipsis width + shrink loop. Labels are
    // short (< 40 chars), so the loop is bounded and cheap.
    const float ell_w = font->CalcTextSizeA(font_h, 3.402823466e+38F, 0.0f, "…").x;
    float budget = max_w - ell_w;
    if (budget <= 0.0f) return 0.0f;

    int len = 0;
    while (text[len] != '\0') {
        const unsigned char c = static_cast<unsigned char>(text[len]);
        int adv = 1;
        if ((c & 0xE0u) == 0xC0u) adv = 2;
        else if ((c & 0xF0u) == 0xE0u) adv = 3;
        else if ((c & 0xF8u) == 0xF0u) adv = 4;
        const int next = len + adv;
        const float w = font->CalcTextSizeA(font_h, 3.402823466e+38F, 0.0f,
                                            text, text + next).x;
        if (w > budget) break;
        len = next;
    }
    if (len <= 0) return 0.0f;
    dl->AddText(pos, color, text, text + len);
    dl->AddText(ImVec2(pos.x + budget, pos.y), color, "…");
    return budget + ell_w;
}

} // namespace woke::render
