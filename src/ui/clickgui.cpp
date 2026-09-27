// ============================================================================
//  woke.wtf — src/ui/clickgui.cpp
//
//  Window chrome drawing. All values trace to the spec or to macOS platform
//  behavior (commented inline). No heap allocation in draw() — fixed buffers,
//  static literals, stack snprintf only.
// ============================================================================

#include "ui/clickgui.h"

#include <cstdio>

#include "ui/animation.h"
#include "ui/theme.h"
#include "utils/math_utils.h"
#include "utils/render_utils.h"

namespace woke::ui {

ClickGui::ClickGui() = default;

void ClickGui::toast(const char *title, const char *message,
                     notifications::Kind kind) {
    toasts_.push(title, message, kind);
}

void ClickGui::draw(float dt) {
    // -- window open/close animation --
    // macOS sheets animate fade+lift on appear (ease_out_cubic for entrances
    // per spec). Submitted every frame while animating so the channel runs;
    // at alpha 0 nothing is drawn and the cost is two float ops.
    float open_t = anim_.value("win.open", open_ ? 1.0f : 0.0f);
    const float open_target = open_ ? 1.0f : 0.0f;
    if (open_t != open_target)
        open_t = anim_.tween("win.open", open_t, open_target,
                             theme::time::window_appear);

    anim_.update(dt);

    if (open_t <= 0.001f) return; // fully closed: no frame cost

    ImGui::SetNextWindowSize(ImVec2(theme::metric::window_w,
                                    theme::metric::window_h),
                              ImGuiCond_Always);
    ImGui::SetNextWindowPos(ImVec2(60.0f, 60.0f), ImGuiCond_Always);
    ImGui::PushStyleVar(ImGuiStyleVar_Alpha, open_t);
    ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding,
                        theme::metric::window_rounding);
    ImGui::PushStyleVar(ImGuiStyleVar_WindowBorderSize, 0.0f);
    ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(0, 0));
    ImGui::PushStyleColor(ImGuiCol_WindowBg, theme::color::window_bg);

    const ImGuiWindowFlags flags =
        ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize |
        ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoScrollbar;

    if (ImGui::Begin("woke_main", nullptr, flags)) {
        ImDrawList *dl = ImGui::GetWindowDrawList();
        const ImVec2 wmin = ImGui::GetWindowPos();
        const ImVec2 wmax = ImVec2(wmin.x + theme::metric::window_w,
                                   wmin.y + theme::metric::window_h);

        // -- 3-layer soft shadow (macOS key-window style) --
        render::soft_shadow(dl, wmin, wmax, theme::metric::window_rounding,
                            14.0f, 90);

        // -- 1px cool stroke (spec) --
        dl->AddRect(wmin, wmax, theme::color::window_stroke,
                    theme::metric::window_rounding, 0,
                    theme::metric::stroke_w);

        draw_title_bar(wmin, wmax, dt);
        draw_sidebar(wmin, wmax, dt);
        draw_cards(wmin, wmax, dt);

        // -- toasts float above everything --
        toasts_.draw(anim_, dt);
    }
    ImGui::End();
    ImGui::PopStyleColor(1);
    ImGui::PopStyleVar(4);

    // -- ESC closes (macOS sheet semantics) --
    if (open_ && ImGui::IsKeyPressed(ImGuiKey_Escape))
        close_requested_ = true;
}

void ClickGui::draw_title_bar(ImVec2 win_min, ImVec2 win_max, float dt) {
    ImDrawList *dl = ImGui::GetWindowDrawList();

    const float h  = theme::metric::title_bar_h;
    const float lx = win_min.x + 16.0f;
    const float ly = win_min.y + h * 0.5f;

    // -- traffic lights --
    // hover = brightness-lerp up over 120ms (spec); press = darken 15%.
    // Channels are per-light so each fades independently.
    struct Light { ImU32 base; const char *name; };
    const Light lights[3] = {
        { theme::color::light_red,    "red"    },
        { theme::color::light_yellow, "yellow" },
        { theme::color::light_green,  "green"  },
    };

    const ImGuiIO &io = ImGui::GetIO();
    for (int i = 0; i < 3; ++i) {
        char key[24];
        std::snprintf(key, sizeof(key), "hl.%s", lights[i].name);
        const ImVec2 c(lx + static_cast<float>(i) * theme::metric::light_spacing,
                       ly);
        const float r = theme::metric::light_radius;

        // Manual hit-test with a forgiving macOS-size target (1.5x radius).
        const bool hov = io.MousePos.x >= c.x - r * 1.5f &&
                         io.MousePos.x <= c.x + r * 1.5f &&
                         io.MousePos.y >= c.y - r * 1.5f &&
                         io.MousePos.y <= c.y + r * 1.5f;
        const bool pressed = hov && io.MouseDown[0];

        const float hov_t = anim_.damp(key, hov ? 1.0f : 0.0f, 0.03f, dt);
        const ImU32 col = pressed
            ? theme::light_press(lights[i].base)
            : theme::blend(lights[i].base,
                           theme::light_hover(lights[i].base), hov_t);
        render::circle(dl, c, r, col);
    }

    // -- centered title (spec: muted gray, centered) --
    const char *title = "woke.wtf — Utility Client";
    const ImVec2 ts = ImGui::CalcTextSize(title);
    dl->AddText(ImVec2((win_min.x + win_max.x - ts.x) * 0.5f,
                       win_min.y + (h - ts.y) * 0.5f),
                theme::color::title_bar_text, title);

    // -- hairline separator under the title bar --
    dl->AddLine(ImVec2(win_min.x, win_min.y + h),
                ImVec2(win_max.x, win_min.y + h),
                theme::color::window_stroke, theme::metric::stroke_w);
}

void ClickGui::draw_sidebar(ImVec2 win_min, ImVec2 win_max, float dt) {
    ImDrawList *dl = ImGui::GetWindowDrawList();

    const float w   = theme::metric::sidebar_w;
    const float top = win_min.y + theme::metric::title_bar_h;

    // Sidebar surface: one lightness step above the window base. macOS
    // source lists sit *lighter* than the content pane — inverted from most
    // dark themes, but it is what makes it read as macOS.
    dl->AddRectFilled(ImVec2(win_min.x, top), ImVec2(win_min.x + w, win_max.y),
                      theme::color::sidebar_bg);

    // -- search field --
    const float pad = 12.0f;
    const ImVec2 smin(win_min.x + pad, top + pad);
    const ImVec2 smax(win_min.x + w - pad, smin.y + theme::metric::search_h);
    if (search_.draw(anim_, smin, smax, dt)) {
        // Filtering is a later step; the bar is wired and readable via
        // search_.text() — demo data is static.
    }

    // -- categories --
    const float item_h = 30.0f;
    float y = smax.y + 10.0f;
    for (int i = 0; i < kMaxCats; ++i) {
        const bool clicked = categories_[i].draw(
            anim_, ImVec2(win_min.x + pad, y),
            ImVec2(win_min.x + w - pad, y + item_h), dt);
        if (clicked && !categories_[i].selected()) {
            for (int k = 0; k < kMaxCats; ++k)
                categories_[k].set_selected(k == i);
            active_category_ = categories_[i].label();
            toasts_.push("Category", categories_[i].label(),
                         notifications::Kind::kInfo);
        }
        y += item_h + 4.0f;
    }
}

void ClickGui::draw_cards(ImVec2 win_min, ImVec2 win_max, float dt) {
    const float w   = theme::metric::sidebar_w;
    const float top = win_min.y + theme::metric::title_bar_h;
    const float cw  = (win_max.x - win_min.x) - w - 24.0f;
    const float ch  = (win_max.y - win_min.y) - theme::metric::title_bar_h - 20.0f;

    ImGui::SetCursorScreenPos(ImVec2(win_min.x + w + 12.0f, top + 10.0f));
    ImGui::BeginChild("cards", ImVec2(cw, ch), ImGuiChildFlags_None,
                      ImGuiWindowFlags_NoBackground);

    // Hit-testing uses screen-space rects, so the card list starts from the
    // child's absolute cursor position (the child itself provides clipping
    // and the scrollbar).
    const ImVec2 origin = ImGui::GetCursorScreenPos();
    const float card_h = theme::metric::card_h;
    const float gap    = theme::metric::card_gap;
    float y = 0.0f;
    for (int i = 0; i < card_count_; ++i) {
        const bool clicked = cards_[i].draw(
            anim_, ImVec2(origin.x, origin.y + y),
            ImVec2(origin.x + cw, origin.y + y + card_h), dt);
        if (clicked)
            toasts_.push(cards_[i].title(), "toggled",
                         notifications::Kind::kInfo);
        y += card_h + gap;
    }

    ImGui::EndChild();
}

} // namespace woke::ui
