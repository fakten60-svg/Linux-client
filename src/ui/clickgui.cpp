// ============================================================================
//  woke.wtf — src/ui/clickgui.cpp
//
//  Window chrome drawing. All values trace to the spec or to macOS platform
//  behavior (commented inline). No heap allocation in draw() — fixed buffers,
//  static literals, one stack buffer for the result counter.
// ============================================================================

#include "ui/clickgui.h"

#include <cstdio>

#include "ui/animation.h"
#include "ui/theme.h"
#include "utils/math_utils.h"
#include "utils/render_utils.h"
#include "utils/string_utils.h"

namespace woke::ui {

ClickGui::ClickGui() = default;

void ClickGui::toast(const char *title, const char *message,
                     notifications::Kind kind) {
    toasts_.push(title, message, kind);
}

// --- filtering --------------------------------------------------------------

bool ClickGui::card_visible(const ModuleCard &card) const {
    if (card.empty()) return false;
    // Index 0 is "All": a pure pass-through, so the category filter only kicks
    // in once the user picks a group.
    if (active_category_ != 0 && card.category() != active_category_)
        return false;
    if (search_.empty()) return true;
    // Match the title or the description — descriptions are truncated on the
    // card, so searching them is how you find a setting you half-remember.
    return text::icontains(card.title(), search_.text()) ||
           text::icontains(card.description(), search_.text());
}

int ClickGui::count_visible() const {
    int n = 0;
    for (int i = 0; i < card_count_; ++i)
        if (card_visible(cards_[i])) ++n;
    return n;
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
    // Channels are per-light so each fades independently; the channel key is
    // a literal here rather than a formatted name.
    struct Light { ImU32 base; const char *key; };
    const Light lights[3] = {
        { theme::color::light_red,    "hl.red"    },
        { theme::color::light_yellow, "hl.yellow" },
        { theme::color::light_green,  "hl.green"  },
    };

    const ImGuiIO &io = ImGui::GetIO();
    for (int i = 0; i < 3; ++i) {
        const ImVec2 c(lx + static_cast<float>(i) * theme::metric::light_spacing,
                       ly);
        const float r = theme::metric::light_radius;

        // Manual hit-test with a forgiving macOS-size target (1.5x radius).
        const bool hov = io.MousePos.x >= c.x - r * 1.5f &&
                         io.MousePos.x <= c.x + r * 1.5f &&
                         io.MousePos.y >= c.y - r * 1.5f &&
                         io.MousePos.y <= c.y + r * 1.5f;
        const bool pressed = hov && io.MouseDown[0];

        const float hov_t = anim_.damp(lights[i].key, hov ? 1.0f : 0.0f, 0.03f, dt);
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
    search_.draw(anim_, smin, smax, dt);

    // -- result counter --
    // Without it a filtered list is indistinguishable from a short one: the
    // user cannot tell "2 matches" from "2 settings exist".
    {
        const int shown = count_visible();
        char buf[32];
        std::snprintf(buf, sizeof(buf), "%d of %d shown", shown, card_count_);
        dl->AddText(ImVec2(smin.x + 2.0f, smax.y + 6.0f),
                    theme::color::text_muted, buf);
    }

    // -- categories --
    const float item_h = 30.0f;
    float y = smax.y + 26.0f;
    for (int i = 0; i < kMaxCats; ++i) {
        const bool clicked = categories_[i].draw(
            anim_, ImVec2(win_min.x + pad, y),
            ImVec2(win_min.x + w - pad, y + item_h), dt);
        if (clicked && !categories_[i].selected()) {
            for (int k = 0; k < kMaxCats; ++k)
                categories_[k].set_selected(k == i);
            active_category_ = i;
            // The card pane is a scrolled list; a group switch that left the
            // scroll offset behind would open mid-list. Reset to the top.
            scroll_reset_ = true;
        }
        y += item_h + 4.0f;
    }
}

void ClickGui::draw_cards(ImVec2 win_min, ImVec2 win_max, float dt) {
    const float w  = theme::metric::sidebar_w;
    const float cw = (win_max.x - win_min.x) - w - 24.0f;
    const float ch = (win_max.y - win_min.y) - theme::metric::title_bar_h - 20.0f;

    // The list scrolls, so the cards must be drawn from the child's *scrolled*
    // content origin rather than a fixed screen position — otherwise the rows
    // never move when the user scrolls (ImGui applies scroll by offsetting the
    // content start, and an absolute origin would override exactly that).
    // Only the child's placement is set here; from then on the cursor belongs
    // to the child and follows its scroll offset.
    ImGui::SetCursorPos(ImVec2(w + 12.0f,
                               theme::metric::title_bar_h + 10.0f));
    ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(0, 0));
    ImGui::BeginChild("cards", ImVec2(cw, ch), ImGuiChildFlags_None,
                      ImGuiWindowFlags_NoBackground);

    if (scroll_reset_) {
        ImGui::SetScrollY(0.0f);
        scroll_reset_ = false;
    }

    const ImVec2 origin = ImGui::GetCursorScreenPos();
    const float card_h  = theme::metric::card_h;
    const float gap     = theme::metric::card_gap;
    const float sb      = ImGui::GetStyle().ScrollbarSize;
    const float card_w  = cw - sb - 4.0f; // reserve the scrollbar gutter

    float y     = 0.0f;
    int   shown = 0;
    for (int i = 0; i < card_count_; ++i) {
        if (!card_visible(cards_[i])) continue;

        const CardEvent ev = cards_[i].draw(
            anim_, ImVec2(origin.x, origin.y + y),
            ImVec2(origin.x + card_w, origin.y + y + card_h), dt);

        switch (ev) {
        case CardEvent::kToggleChanged:
            toasts_.push(cards_[i].title(),
                         cards_[i].is_on() ? "enabled" : "disabled",
                         cards_[i].is_on() ? notifications::Kind::kSuccess
                                           : notifications::Kind::kInfo);
            break;
        case CardEvent::kBodyClicked:
            // The description is clipped on the card, so the toast is where you
            // read it in full.
            toasts_.push(cards_[i].title(), cards_[i].description(),
                         notifications::Kind::kInfo);
            break;
        case CardEvent::kNone:
            break;
        }

        y += card_h + gap;
        ++shown;
    }

    // Claim the scroll extent. Cards are drawn onto the draw list and never
    // advance the cursor, so without this the child reports zero content
    // height and everything past the first screenful is unreachable.
    const float total = shown > 0 ? y - gap : 0.0f;
    ImGui::Dummy(ImVec2(cw - sb, total));

    diag_.visible_cards = shown;
    diag_.scroll_max_y  = ImGui::GetScrollMaxY();

    if (shown == 0) {
        const char *msg = "No settings match";
        const ImVec2 ts = ImGui::CalcTextSize(msg);
        ImGui::GetWindowDrawList()->AddText(
            ImVec2(origin.x + (card_w - ts.x) * 0.5f, origin.y + 40.0f),
            theme::color::text_muted, msg);
    }

    ImGui::EndChild();
    ImGui::PopStyleVar(1);
}

} // namespace woke::ui
