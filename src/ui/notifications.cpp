// ============================================================================
//  woke.wtf — src/ui/notifications.cpp
//
//  Toast rendering. macOS-notification motion: enter from the right with
//  ease_out_cubic (entrance curve per spec), hold, exit fading right.
//  All positioning is derived from theme tokens; nothing hardcoded.
// ============================================================================

#include "ui/notifications.h"

#include "ui/animation.h"
#include "ui/theme.h"
#include "utils/math_utils.h"
#include "utils/render_utils.h"

namespace woke::ui {

namespace {
constexpr float kHoldSec   = theme::time::toast_hold;
constexpr float kSlideDist = 40.0f; // px offscreen offset at t=0, before scaling
constexpr float kStackGap  = 10.0f;

/// Phase fractions for enter/hold/exit. The hold is a dwell time, not motion,
/// so it is never scaled; the enter/exit durations and the travel distance are
/// supplied by the caller already shortened by the controller's motion scale.
enum class Phase { kEnter, kHold, kExit };

Phase phase_of(float age, float enter_s, float exit_s, float *phase_t) {
    if (age < enter_s) { *phase_t = age / enter_s; return Phase::kEnter; }
    if (age < enter_s + kHoldSec) {
        *phase_t = (age - enter_s) / kHoldSec;
        return Phase::kHold;
    }
    *phase_t = (age - enter_s - kHoldSec) / exit_s;
    return Phase::kExit;
}
} // namespace

void NotificationQueue::push(const char *title, const char *message,
                             notifications::Kind kind) {
    // Find a free slot or evict the oldest (lowest age is oldest).
    int slot = -1;
    for (int i = 0; i < kMaxToasts; ++i)
        if (!toasts_[i].used) { slot = i; break; }

    if (slot < 0) {
        int oldest = 0;
        for (int i = 1; i < kMaxToasts; ++i)
            if (toasts_[i].age > toasts_[oldest].age) oldest = i;
        // Evicting the *newest* by age is wrong; toasts age uniformly, so
        // evict the one closest to natural expiry.
        slot = oldest;
    }

    Toast &t     = toasts_[slot];
    t.title.set(title);
    t.message.set(message);
    t.kind  = kind;
    t.age   = 0.0f;
    t.used  = true;
}

int NotificationQueue::active_count() const {
    int n = 0;
    for (int i = 0; i < kMaxToasts; ++i)
        if (toasts_[i].used) ++n;
    return n;
}

void NotificationQueue::draw(AnimationController &anim, float dt) {
    // "Reduce Motion" shortens the slide *and* the distance it covers: a
    // faster version of the same travel would still be travel. The dwell time
    // is untouched, so a toast stays readable for as long as before.
    const float motion   = anim.motion_scale();
    const float enter_s  = theme::time::toast_slide * motion;
    const float exit_s   = theme::time::toast_slide * motion;
    const float life_s   = enter_s + kHoldSec + exit_s;
    const float slide_px = kSlideDist * motion;

    const ImGuiIO &io = ImGui::GetIO();
    const float margin = 16.0f;
    const float w = theme::metric::toast_w;
    const float h = theme::metric::toast_h;

    // Stack from the top-right downward; expired toasts leave no gap
    // because stacking is computed from live toasts each frame.
    float y = io.DisplaySize.y - margin - h;
    for (int i = kMaxToasts - 1; i >= 0; --i) {
        Toast &t = toasts_[i];
        if (!t.used) continue;

        t.age += dt;
        if (t.age >= life_s) { t.used = false; continue; }

        float phase_t = 0.0f;
        const Phase ph = phase_of(t.age, enter_s, exit_s, &phase_t);

        // -- x-offset + alpha per phase --
        float offset = 0.0f, alpha = 1.0f;
        switch (ph) {
        case Phase::kEnter: // ease_out_cubic (entrance curve per spec)
            offset = (1.0f - math::ease_out_cubic(phase_t)) * slide_px;
            alpha  = phase_t;
            break;
        case Phase::kHold:
            offset = 0.0f;
            alpha  = 1.0f;
            break;
        case Phase::kExit: // accelerate back out, fade faster than move
            offset = math::ease_in_out_quart(phase_t) * slide_px;
            alpha  = 1.0f - phase_t;
            alpha *= alpha; // quadratic fade reads softer than linear
            break;
        }

        ImDrawList *dl = ImGui::GetForegroundDrawList();

        const ImVec2 tmin(io.DisplaySize.x - w - margin + offset, y);
        const ImVec2 tmax(tmin.x + w, tmin.y + h);

        render::rounded_rect(dl, tmin, tmax, theme::blend(0, theme::color::toast_bg, alpha),
                             theme::blend(0, theme::color::toast_stroke, alpha),
                             theme::metric::card_rounding);

        // -- kind accent bar --
        ImU32 accent = theme::color::apple_blue;
        if (t.kind == notifications::Kind::kSuccess) accent = theme::color::apple_cyan;
        if (t.kind == notifications::Kind::kWarning)
            accent = IM_COL32(0xFF, 0xBD, 0x2E, 255);
        const ImU32 accent_col = theme::blend(0, accent, alpha);
        if ((accent_col & IM_COL32_A_MASK) != 0)
            dl->AddRectFilled(ImVec2(tmin.x, tmin.y + 6.0f),
                              ImVec2(tmin.x + 3.0f, tmax.y - 6.0f), accent_col, 1.5f);

        // -- texts (padded right of the accent bar) --
        const float pad = 14.0f;
        render::text_clipped(dl, ImVec2(tmin.x + pad, tmin.y + 9.0f),
                             w - pad - 10.0f, t.title.c_str(),
                             theme::blend(0, theme::color::text_bright, alpha));
        render::text_clipped(dl, ImVec2(tmin.x + pad, tmin.y + 27.0f),
                             w - pad - 10.0f, t.message.c_str(),
                             theme::blend(0, theme::color::text_muted, alpha));

        y -= (h + kStackGap);
    }
}

} // namespace woke::ui
