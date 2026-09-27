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
constexpr float kEnterSec  = theme::time::toast_slide;
constexpr float kHoldSec   = theme::time::toast_hold;
constexpr float kExitSec   = theme::time::toast_slide;
constexpr float kLifeSec   = kEnterSec + kHoldSec + kExitSec;
constexpr float kSlideDist = 40.0f; // px offscreen offset at t=0
constexpr float kStackGap  = 10.0f;

/// Phase fractions for enter/hold/exit. Computed once — no per-frame alloc.
enum class Phase { kEnter, kHold, kExit };

Phase phase_of(float age, float *phase_t) {
    if (age < kEnterSec) { *phase_t = age / kEnterSec; return Phase::kEnter; }
    if (age < kEnterSec + kHoldSec) {
        *phase_t = (age - kEnterSec) / kHoldSec;
        return Phase::kHold;
    }
    *phase_t = (age - kEnterSec - kHoldSec) / kExitSec;
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
    (void)anim;

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
        if (t.age >= kLifeSec) { t.used = false; continue; }

        float phase_t = 0.0f;
        const Phase ph = phase_of(t.age, &phase_t);

        // -- x-offset + alpha per phase --
        float offset = 0.0f, alpha = 1.0f;
        switch (ph) {
        case Phase::kEnter: // ease_out_cubic (entrance curve per spec)
            offset = (1.0f - math::ease_out_cubic(phase_t)) * kSlideDist;
            alpha  = phase_t;
            break;
        case Phase::kHold:
            offset = 0.0f;
            alpha  = 1.0f;
            break;
        case Phase::kExit: // accelerate back out, fade faster than move
            offset = math::ease_in_out_quart(phase_t) * kSlideDist;
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
