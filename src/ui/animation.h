// ============================================================================
//  woke.wtf — src/ui/animation.h
//
//  Central animation controller with named float channels.
//
//  Design: every animated UI property owns a channel keyed by a short name
//  ("window.open", "pill.Fly", "light.hover.red", ...). Channels live in a
//  fixed pool — no heap allocation at runtime, satisfying the zero-
//  allocation rule for render loops. Lookup is a linear scan over at most
//  kMaxChannels 64-char-free fixed keys, which at UI scale (a few dozen
//  live channels) is cheaper than hashing and keeps the code trivial.
//
//  Two update modes:
//   * tween  — timed, easing-curve based (entrances, retargets)
//   * damp   — frame-rate-independent exponential smoothing (hover, knob)
//
//  Not thread-safe by design: the UI thread is the only writer.
// ============================================================================

#pragma once

#include <cstddef>
#include <cstdint>

namespace woke::ui {

class AnimationController {
public:
    static constexpr size_t kMaxChannels = 128;

    enum class Mode : uint8_t { kTween, kDamp };

    struct Channel {
        char key[24];         // copied on claim — callers may pass stack buffers
        float value;
        float from;
        float target;
        float elapsed;
        float duration;
        float smoothing; // damp mode: seconds to close ~87%
        Mode        mode;
        bool        active;
    };

    /// Advance all active channels. Call once per frame before rendering.
    void update(float dt);

    /// Timed animation: value walks from `from` to `target` over `duration`
    /// seconds with ease_in_out_quart. Returns the channel's current value.
    float tween(const char *key, float from, float target, float duration);

    /// Exponential smoothing toward `target`. Returns the current value.
    float damp(const char *key, float target, float smoothing, float dt);

    /// Read a channel without advancing it; inactive channels read as `target`.
    float value(const char *key, float fallback = 0.0f) const;

    /// True if a channel exists and is still moving.
    bool active(const char *key) const;

    /// Reset all channels (called on window teardown).
    void reset();

    size_t active_count() const;

private:
    static constexpr size_t kNotFound = static_cast<size_t>(-1);
    Channel channels_[kMaxChannels] = {};
    size_t  count_ = 0;

    int find(const char *key) const;
    Channel &claim(const char *key);
};

} // namespace woke::ui
