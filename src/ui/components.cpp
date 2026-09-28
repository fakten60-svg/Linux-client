// ============================================================================
//  woke.wtf — src/ui/components.cpp
//
//  Rationale notes live inline at each drawing decision — every value ties
//  back to either a spec line or macOS platform behavior.
// ============================================================================

#include "ui/components.h"

#include "ui/animation.h"
#include "ui/theme.h"
#include "utils/math_utils.h"
#include "utils/render_utils.h"

namespace woke::ui {

// --- BaseUIComponent --------------------------------------------------------

BaseUIComponent::BaseUIComponent(const char *prefix, const char *id) {
    // "prefix.id" into the fixed key buffer, truncated rather than overflowed.
    // Done once, at construction — draw() only ever reads key().
    size_t n = 0;
    const auto append = [&n, this](const char *s) {
        for (; s != nullptr && *s != '\0' && n + 1 < sizeof(key_); ++s)
            key_[n++] = *s;
    };
    append(prefix);
    append(".");
    append(id);
    key_[n] = '\0';
}

bool BaseUIComponent::tick(ImVec2 min, ImVec2 max, bool enabled) {
    if (!enabled) {
        hovered_ = pressed_ = false;
        return false;
    }
    const ImGuiIO &io = ImGui::GetIO();
    const ImVec2  mp  = io.MousePos;
    hovered_ = mp.x >= min.x && mp.x <= max.x &&
               mp.y >= min.y && mp.y <= max.y;
    pressed_ = hovered_ && io.MouseDown[0];

    // Click = press began inside the rect this frame (macOS buttons commit
    // on mouse-down for small controls like pills; it feels snappier).
    return hovered_ && ImGui::IsMouseClicked(0);
}

// --- PillToggle -------------------------------------------------------------

// PillToggle(const char*, bool) is defined inline in the header.

PillToggle::State PillToggle::draw(AnimationController &anim, ImVec2 min,
                                   ImVec2 max, float dt, bool enabled) {
    const char *key_t = key();

    const bool clicked = tick(min, max, enabled);
    if (clicked) state_on_ = !state_on_;

    // Retarget + advance through the shared controller (ease_in_out_quart,
    // theme::time::knob = 180ms — Apple's NSSwitch cadence, shortened by the
    // controller's motion scale).
    // An absent channel reads as *off*, not as the target: seeding it with the
    // target would make the first flip an instant jump with no knob travel.
    const float target = state_on_ ? 1.0f : 0.0f;
    float t = anim.value(key_t, 0.0f);
    if (t != target) t = anim.tween(key_t, t, target, theme::time::knob);

    // -- track --
    // Cross-fade Dark Gray -> Apple Blue per spec; cyan rides in as the
    // lower gradient stop only in the on-state (NSSwitch two-stop blue).
    const ImU32 track    = theme::blend(theme::color::pill_off,
                                        theme::color::apple_blue, t);
    const ImU32 track_lo = theme::blend(track, theme::color::apple_cyan,
                                        0.35f * t);
    ImDrawList *dl = ImGui::GetWindowDrawList();
    const float radius = (max.y - min.y) * 0.5f;
    render::gradient_fill(dl, min, max, track, track_lo, radius);

    // -- knob --
    const float inset  = theme::metric::pill_knob_inset;
    const float travel = (max.x - min.x) - (max.y - min.y);
    const float r      = radius - inset;
    const float cx     = min.x + radius + travel * t;
    const float cy     = (min.y + max.y) * 0.5f;

    // Knob shadow only while settled: macOS drops it mid-flight because the
    // motion itself communicates elevation.
    if (t <= 0.0f || t >= 1.0f)
        render::circle(dl, ImVec2(cx, cy + 1.0f), r, theme::color::knob_shadow);
    render::circle(dl, ImVec2(cx, cy), r, theme::color::knob);

    return State{ state_on_ };
}

// --- ModuleCard -------------------------------------------------------------

ModuleCard::ModuleCard(const char *title, const char *description,
                       const char *keybind, int category, bool initial_on)
    : BaseUIComponent("mc", title), title_(title), description_(description),
      keybind_(keybind), category_(category), toggle_(title, initial_on) {}

CardEvent ModuleCard::draw(AnimationController &anim, ImVec2 min, ImVec2 max,
                           float dt) {
    if (title_ == nullptr) return CardEvent::kNone; // spare slot — draw nothing

    ImDrawList *dl = ImGui::GetWindowDrawList();

    // The pill rect is known before hit-testing, and it is exactly what
    // disambiguates a row click from a switch click: both rects overlap, so
    // without this one press would toggle the switch AND report a row click.
    const float  pad = theme::metric::card_padding;
    const float  pw  = theme::metric::pill_w;
    const float  ph  = theme::metric::pill_h;
    const ImVec2 pmin(max.x - pad - pw, (min.y + max.y) * 0.5f - ph * 0.5f);
    const ImVec2 pmax(pmin.x + pw, pmin.y + ph);

    // Card hover — exponential smoothing (Apple hover semantics, ~50ms to
    // 87%): rows must feel instant, not springy. Hover still covers the whole
    // row (macOS highlights the row while the pointer is over the switch).
    const bool   row_clicked = tick(min, max);
    const ImVec2 mp          = ImGui::GetIO().MousePos;
    const bool   on_switch   = mp.x >= pmin.x - 2.0f && mp.x <= pmax.x + 2.0f;
    const float  hov = anim.damp(key(), hovered_ ? 1.0f : 0.0f, 0.05f, dt);

    // Hover shifts the card one lightness step up (macOS row-hover), never
    // toward accent color — accent is reserved for selection and state.
    const ImU32 fill = theme::blend(theme::color::card_bg,
                                    theme::color::card_bg_hover, hov);
    render::rounded_rect(dl, min, max, fill, theme::color::card_stroke,
                         theme::metric::card_rounding);

    // -- text block --
    const float col_x  = min.x + pad;
    const float col_w  = (max.x - min.x) - pad * 2.0f - 96.0f;
    float       y      = min.y + 11.0f;

    // Title — white per spec; Description — muted gray per spec.
    render::text_clipped(dl, ImVec2(col_x, y), col_w, title_,
                         theme::color::text_bright);
    y += 20.0f;
    render::text_clipped(dl, ImVec2(col_x, y), col_w, description_,
                         theme::color::text_muted);

    // -- keybind badge (right edge, before the pill) --
    // macOS shows keyboard affordances as small rounded key caps.
    if (keybind_ != nullptr && keybind_[0] != '\0') {
        const ImVec2 ts = ImGui::CalcTextSize(keybind_);
        const float  bw = ts.x + 12.0f;
        const float  bh = 17.0f;
        const float  cx = max.x - pad - theme::metric::pill_w - 10.0f - bw;
        const ImVec2 bmin(cx, (min.y + max.y) * 0.5f - bh * 0.5f);
        const ImVec2 bmax(bmin.x + bw, bmin.y + bh);
        render::rounded_rect(dl, bmin, bmax, 0, theme::color::card_stroke, 4.0f);
        render::text(dl, ImVec2(bmin.x + 6.0f, bmin.y + (bh - ts.y) * 0.5f),
                     theme::color::text_muted, keybind_);
    }

    // -- pill --
    const bool was_on = toggle_.is_on();
    toggle_.draw(anim, pmin, pmax, dt);

    if (toggle_.is_on() != was_on) return CardEvent::kToggleChanged;
    if (row_clicked && !on_switch) return CardEvent::kBodyClicked;
    return CardEvent::kNone;
}

// --- CategoryItem -----------------------------------------------------------

bool CategoryItem::draw(AnimationController &anim, ImVec2 min, ImVec2 max,
                        float dt) {
    ImDrawList *dl = ImGui::GetWindowDrawList();
    const bool clicked = tick(min, max);

    const float target = (hovered_ || selected_) ? 1.0f : 0.0f;
    const float hov    = anim.damp(key(), target, 0.05f, dt);

    // Selection = 14% Apple Blue wash; hover alone is a neutral lift. The
    // distinction matters: selection is state, hover is only attention.
    const ImU32 fill = theme::blend(IM_COL32(255, 255, 255, 14),
                                    theme::color::selection_bg, hov);
    render::rounded_rect(dl, min, max, fill, 0, 6.0f);

    // Accent bar: 3px rounded, fades with the same channel so it never pops.
    const ImU32 bar = theme::blend(0, theme::color::apple_blue, hov);
    if ((bar & IM_COL32_A_MASK) != 0)
        render::rounded_rect(dl, ImVec2(min.x, min.y + 4.0f),
                             ImVec2(min.x + 3.0f, max.y - 4.0f), bar, 0, 1.5f);

    const float th = ImGui::GetTextLineHeight();
    render::text(dl, ImVec2(min.x + 12.0f, (min.y + max.y - th) * 0.5f),
                 selected_ ? theme::color::text_primary
                           : theme::color::text_muted,
                 label_);
    return clicked;
}

// --- SearchBar --------------------------------------------------------------

SearchBar::SearchBar() : BaseUIComponent("sb", "search") {}

void SearchBar::set_text(const char *text) {
    size_t n = 0;
    if (text != nullptr) {
        for (; text[n] != '\0' && n + 1 < sizeof(buf_); ++n) buf_[n] = text[n];
    }
    buf_[n] = '\0';
}

bool SearchBar::draw(AnimationController &anim, ImVec2 min, ImVec2 max,
                     float dt) {
    (void)anim; (void)dt;
    ImDrawList *dl = ImGui::GetWindowDrawList();

    render::rounded_rect(dl, min, max, theme::color::field_bg,
                         focused_ ? theme::color::apple_blue
                                  : theme::color::field_stroke,
                         7.0f);

    // Magnifier glyph from primitives — no icon font dependency.
    const float cy = (min.y + max.y) * 0.5f;
    render::circle(dl, ImVec2(min.x + 14.0f, cy - 1.0f), 4.0f, 0,
                   theme::color::text_muted, 1.3f);
    render::line(dl, ImVec2(min.x + 17.0f, cy + 2.0f),
                 ImVec2(min.x + 19.5f, cy + 4.5f),
                 theme::color::text_muted, 1.3f);

    // Input region — an ImGui input whose frame we drew ourselves.
    ImGui::SetCursorScreenPos(ImVec2(min.x + 26.0f, min.y + 4.0f));
    ImGui::PushItemWidth((max.x - min.x) - 34.0f);
    ImGui::PushStyleColor(ImGuiCol_FrameBg, IM_COL32(0, 0, 0, 0));
    ImGui::PushStyleColor(ImGuiCol_Text, theme::color::text_primary);

    const bool changed = ImGui::InputTextWithHint("##woke_search", "Search",
                                                  buf_, sizeof(buf_));
    focused_ = ImGui::IsItemFocused(); // stroke state for the next frame

    ImGui::PopStyleColor(2);
    ImGui::PopItemWidth();
    return changed;
}

} // namespace woke::ui
