// ============================================================================
//  woke.wtf — src/ui/animation.cpp
// ============================================================================

#include "ui/animation.h"

#include <cmath>
#include <cstring>

#include "utils/math_utils.h"

namespace woke::ui {

void AnimationController::update(float dt) {
    for (size_t i = 0; i < count_; ++i) {
        Channel &c = channels_[i];
        if (!c.active || c.key[0] == '\0') continue;

        switch (c.mode) {
        case Mode::kTween:
            c.elapsed += dt;
            if (c.elapsed >= c.duration) {
                c.value  = c.target;
                c.active = false;
            } else {
                const float t = c.elapsed / c.duration;
                c.value = math::lerp(c.from, c.target, math::ease_in_out_quart(t));
            }
            break;

        case Mode::kDamp:
            c.value = math::spring_damp(c.value, c.target, c.smoothing, dt);
            // Converged check: within a tenth of a pixel/alpha step we stop
            // iterating so idle channels cost nothing in later frames.
            if (std::fabs(c.target - c.value) < 0.001f) {
                c.value  = c.target;
                c.active = false;
            }
            break;
        }
    }
}

float AnimationController::tween(const char *key, float from, float target,
                                 float duration) {
    Channel &c = claim(key);
    if (c.active && c.mode == Mode::kTween && c.target == target) {
        return c.value; // already animating toward the same target — no restart
    }
    c.mode      = Mode::kTween;
    c.from      = from;
    c.target    = target;
    c.duration  = duration;
    c.elapsed   = 0.0f;
    c.active    = true;
    return c.value = from;
}

float AnimationController::damp(const char *key, float target, float smoothing,
                                float dt) {
    Channel &c = claim(key);
    if (c.key[0] == '\0' && c.value == 0.0f && c.target == 0.0f) {
        // Fresh channel: start at the target so first-frame reads are sane.
        c.value = target;
    }
    c.mode      = Mode::kDamp;
    c.target    = target;
    c.smoothing = smoothing;
    c.active    = true;
    // Stepping happens exclusively in update(dt) — stepping here too would
    // integrate dt twice and make hover speeds depend on call order.
    return c.value;
}

float AnimationController::value(const char *key, float fallback) const {
    const int i = find(key);
    return i < 0 ? fallback : channels_[static_cast<size_t>(i)].value;
}

bool AnimationController::active(const char *key) const {
    const int i = find(key);
    return i >= 0 && channels_[static_cast<size_t>(i)].active;
}

void AnimationController::reset() {
    for (size_t i = 0; i < count_; ++i) channels_[i] = Channel{};
    count_ = 0;
}

size_t AnimationController::active_count() const {
    size_t n = 0;
    for (size_t i = 0; i < count_; ++i)
        if (channels_[i].active) ++n;
    return n;
}

int AnimationController::find(const char *key) const {
    if (key == nullptr || key[0] == '\0') return -1;
    for (size_t i = 0; i < count_; ++i)
        if (channels_[i].key[0] != '\0' &&
            std::strncmp(channels_[i].key, key, sizeof(Channel::key) - 1) == 0)
            return static_cast<int>(i);
    return -1;
}

AnimationController::Channel &AnimationController::claim(const char *key) {
    if (key == nullptr || key[0] == '\0') key = "?";

    const int i = find(key);
    if (i >= 0) return channels_[static_cast<size_t>(i)];

    if (count_ < kMaxChannels) {
        Channel &c = channels_[count_++];
        c = Channel{};
        // Truncation-safe copy: keys are short dotted paths ("pill.Fly");
        // strncmp matching above makes a truncated tail still address the
        // same slot.
        std::strncpy(c.key, key, sizeof(c.key) - 1);
        c.key[sizeof(c.key) - 1] = '\0';
        return c;
    }

    // Pool exhausted: overwrite the oldest slot rather than crash or allocate.
    // A reused key means two widgets share one channel — visually a stuck
    // animation, never memory unsafety.
    Channel &oldest = channels_[0];
    std::strncpy(oldest.key, key, sizeof(oldest.key) - 1);
    oldest.key[sizeof(oldest.key) - 1] = '\0';
    oldest.active = false;
    return oldest;
}

} // namespace woke::ui
