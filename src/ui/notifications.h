// ============================================================================
//  woke.wtf — src/ui/notifications.h
//
//  Toast queue (spec 2i). macOS-notification semantics: slide in from the
//  right, hold, slide out; newest at the bottom; queue cap with oldest-drop.
//
//  Zero-allocation rule: titles/messages live in fixed char buffers
//  (FixedString<N>) copied at push() time — callers may pass stack or
//  string-literal text; nothing is retained by pointer.
// ============================================================================

#pragma once

#include <imgui.h>

namespace woke::ui {

class AnimationController;

namespace notifications {

/// Bounded string; truncates silently rather than allocating.
template <int N>
struct FixedString {
    char buf[N] = {};

    void set(const char *src) {
        if (src == nullptr) { buf[0] = '\0'; return; }
        int i = 0;
        for (; i < N - 1 && src[i] != '\0'; ++i) buf[i] = src[i];
        buf[i] = '\0';
    }
    const char *c_str() const { return buf; }
};

enum class Kind : int { kInfo, kSuccess, kWarning };

} // namespace notifications

class NotificationQueue {
public:
    static constexpr int kMaxToasts = 6;

    struct Toast {
        notifications::FixedString<40>  title;
        notifications::FixedString<96>  message;
        notifications::Kind             kind = notifications::Kind::kInfo;
        float                           age = 0.0f;      // seconds since push
        bool                            used = false;
    };

    /// Queue a toast; drops the oldest when the queue is full.
    void push(const char *title, const char *message,
              notifications::Kind kind = notifications::Kind::kInfo);

    /// Advance lifetimes and draw. Top-right anchored with slide/fade.
    void draw(AnimationController &anim, float dt);

    int active_count() const;

private:
    Toast toasts_[kMaxToasts] = {};
};

} // namespace woke::ui
