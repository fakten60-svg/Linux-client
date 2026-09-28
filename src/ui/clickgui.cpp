// ============================================================================
//  woke.wtf — src/ui/clickgui.cpp
//
//  Window chrome drawing. All values trace to the spec or to macOS platform
//  behavior (commented inline). No heap allocation in draw() — fixed buffers,
//  static literals, one stack buffer for the result counter.
// ============================================================================

#include "ui/clickgui.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>

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

// --- settings persistence ---------------------------------------------------

void ClickGui::set_config_path(const char *path) {
    if (path == nullptr) path = "";
    std::strncpy(config_path_, path, sizeof(config_path_) - 1);
    config_path_[sizeof(config_path_) - 1] = '\0';
}

void ClickGui::card_key(int index, char *out, int cap) const {
    // Titles are the human-facing name and also the identity of a card
    // everywhere else (animation channels), so the settings key is derived
    // from them rather than from the array position: reordering cards must not
    // silently reinterpret an existing file.
    char slug[40];
    text::slugify(cards_[index].title(), slug, static_cast<int>(sizeof(slug)));
    std::snprintf(out, static_cast<size_t>(cap), "card.%s", slug);
}

bool ClickGui::apply_setting(const char *key, const char *value) {
    if (key == nullptr) return false;

    if (std::strcmp(key, "ui.category") == 0) {
        const int idx = std::atoi(value);
        if (idx < 0 || idx >= kMaxCats) return false;
        for (int k = 0; k < kMaxCats; ++k)
            categories_[k].set_selected(k == idx);
        active_category_ = idx;
        return true;
    }

    if (std::strncmp(key, "card.", 5) == 0) {
        char candidate[48];
        for (int i = 0; i < card_count_; ++i) {
            card_key(i, candidate, static_cast<int>(sizeof(candidate)));
            if (std::strcmp(candidate, key) != 0) continue;
            // Round-trip through the store so "card.x = on" is parsed by the
            // same boolean rules a hand-edited file gets.
            config_.set(key, value);
            cards_[i].set_on(config_.get_bool(key, false));
            return true;
        }
    }

    return false;
}

int ClickGui::load_config() {
    if (!config_enabled()) return 0;

    config_.clear();
    // Missing file (first run) leaves the store empty and the factory defaults
    // in place — that is the normal path, not a failure to report.
    if (!config_.load(config_path_)) return 0;

    int applied = 0;
    for (int i = 0; i < config_.count(); ++i)
        if (apply_setting(config_.entry(i).key, config_.entry(i).value))
            ++applied;

    config_dirty_ = false;
    return applied;
}

void ClickGui::refresh_settings() {
    char buf[16];
    std::snprintf(buf, sizeof(buf), "%d", active_category_);
    config_.set("ui.category", buf);

    char key[48];
    for (int i = 0; i < card_count_; ++i) {
        card_key(i, key, static_cast<int>(sizeof(key)));
        config_.set_bool(key, cards_[i].is_on());
    }
}

bool ClickGui::save_config() {
    if (!config_enabled()) return false;

    // Write the whole live state, not just what changed: the file then always
    // describes the current configuration, and unknown keys loaded from a
    // newer build survive because they are still in the store.
    refresh_settings();
    const bool ok = config_.save(config_path_);
    if (ok) config_dirty_ = false;
    return ok;
}

bool ClickGui::set_setting(const char *key, const char *value) {
    if (!apply_setting(key, value)) return false;
    // A programmatic edit is a user-visible change like any other: it must be
    // written back, whether that happens now (autosave) or on exit.
    config_dirty_ = true;
    return true;
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
    // -- accessibility: "Reduce Motion" --
    // Read from the card every frame, so flipping the switch takes effect on
    // the next animation (including the toast slide, which reads the same
    // scale). One place honours the preference for the whole UI.
    anim_.set_motion_scale(reduced_motion() ? theme::time::reduced_motion_scale
                                            : 1.0f);
    diag_.motion_scale    = anim_.motion_scale();
    diag_.window_appear_s = anim_.scaled(theme::time::window_appear);

    // Whole-list switch census (independent of the filters): the harness reads
    // it to prove a settings file actually restored state across processes.
    int on = 0;
    for (int i = 0; i < card_count_; ++i)
        if (!cards_[i].empty() && cards_[i].is_on()) ++on;
    diag_.enabled_cards = on;

    // -- window open/close animation --
    // macOS sheets cross-fade on appear (ease_in_out_quart here, the curve the
    // controller applies). Submitted every frame while animating so the channel
    // runs; at alpha 0 nothing is drawn and the cost is two float ops.
    // An absent channel reads as fully closed (0.0), NOT as the current target:
    // seeding it with the target makes the very first tween a no-op and the
    // window would pop in with no animation at all.
    float open_t = anim_.value("win.open", 0.0f);
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

        // The chrome is drawn by hand, so the appear/close fade has to be
        // applied by hand too — see the ambient-alpha note in render_utils.h.
        // One factor covers the shadow, shell, sidebar, cards and text, which
        // is what makes open_t an actual cross-fade rather than a pop.
        render::set_ambient_alpha(open_t);

        // -- 3-layer soft shadow (macOS key-window style) --
        render::soft_shadow(dl, wmin, wmax, theme::metric::window_rounding,
                            14.0f, 90);

        // -- 1px cool stroke (spec) --
        render::rounded_rect(dl, wmin, wmax, 0, theme::color::window_stroke,
                             theme::metric::window_rounding,
                             theme::metric::stroke_w);

        draw_title_bar(wmin, wmax, dt);
        draw_sidebar(wmin, wmax, dt);
        draw_cards(wmin, wmax, dt);

        // -- toasts float above everything --
        // They carry their own per-toast alpha, so the ambient factor is
        // released before they draw — and released even when the GUI is
        // closed, so nothing else in the frame inherits it.
        render::set_ambient_alpha(1.0f);
        toasts_.draw(anim_, dt);
    }
    render::set_ambient_alpha(1.0f);
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
    render::text(dl, ImVec2((win_min.x + win_max.x - ts.x) * 0.5f,
                            win_min.y + (h - ts.y) * 0.5f),
                 theme::color::title_bar_text, title);

    // -- hairline separator under the title bar --
    render::line(dl, ImVec2(win_min.x, win_min.y + h),
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
    render::rounded_rect(dl, ImVec2(win_min.x, top),
                         ImVec2(win_min.x + w, win_max.y),
                         theme::color::sidebar_bg, 0, 0.0f);

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
        render::text(dl, ImVec2(smin.x + 2.0f, smax.y + 6.0f),
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
            config_dirty_    = true; // the active group is a persisted setting
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
        case CardEvent::kToggleChanged: {
            const bool on = cards_[i].is_on();
            config_dirty_ = true; // every switch is a persisted setting
            // The accessibility switch reports what it actually did to the UI
            // rather than a generic on/off, so the change is visible in the
            // window itself as well as in the toast.
            const bool is_motion = (i == kReducedMotionIndex);
            toasts_.push(cards_[i].title(),
                         is_motion ? (on ? "animations shortened"
                                         : "animations at full speed")
                                   : (on ? "enabled" : "disabled"),
                         on ? notifications::Kind::kSuccess
                            : notifications::Kind::kInfo);
            break;
        }
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
        render::text(ImGui::GetWindowDrawList(),
                     ImVec2(origin.x + (card_w - ts.x) * 0.5f,
                            origin.y + 40.0f),
                     theme::color::text_muted, msg);
    }

    ImGui::EndChild();
    ImGui::PopStyleVar(1);
}

} // namespace woke::ui
