// ============================================================================
//  woke.wtf — tests/unit/test_animation.cpp
//
//  src/ui/animation.{h,cpp}. The controller is shared mutable state for the
//  whole UI, so the properties worth pinning are the ones every widget
//  depends on without knowing it: that a channel lands exactly on its target,
//  that re-requesting the target you are already animating to does not
//  restart the motion, that channels are addressed by copied key, and that
//  the fixed pool degrades instead of overflowing.
// ============================================================================

#include <cstdio>

#include "test_util.h"

#include "ui/animation.h"

using woke::ui::AnimationController;

namespace {
constexpr float kEps = 1e-4f;

void make_key(char *out, int cap, int i) {
    std::snprintf(out, static_cast<size_t>(cap), "k%d", i);
}
} // namespace

void run_animation_tests() {
    // -- tween ---------------------------------------------------------------
    WOKE_SUITE("AnimationController::tween");
    {
        AnimationController a;

        // An absent channel reads as the caller's fallback, not as 0.
        WOKE_CHECK_NEAR(a.value("nope", -1.0f), -1.0, kEps);
        WOKE_CHECK_FALSE(a.active("nope"));

        // A fresh tween starts at `from` and reports it immediately, so the
        // first drawn frame is at the start of the motion, never at the target.
        WOKE_CHECK_NEAR(a.tween("k", 0.0f, 1.0f, 1.0f), 0.0, kEps);
        WOKE_CHECK_NEAR(a.value("k"), 0.0, kEps);
        WOKE_CHECK(a.active("k"));

        // Mid-flight is the easing curve, i.e. the midpoint at t = 0.5.
        a.update(0.5f);
        WOKE_CHECK_NEAR(a.value("k"), 0.5, kEps);
        WOKE_CHECK(a.active("k"));

        // Overshooting the duration lands exactly on the target and stops.
        a.update(0.5f);
        WOKE_CHECK_NEAR(a.value("k"), 1.0, kEps);
        WOKE_CHECK_FALSE(a.active("k"));

        // A finished channel is inert: further updates cannot move it.
        a.update(10.0f);
        WOKE_CHECK_NEAR(a.value("k"), 1.0, kEps);
    }

    WOKE_SUITE("AnimationController::tween retarget");
    {
        AnimationController a;
        a.tween("k", 0.0f, 1.0f, 1.0f);
        a.update(0.25f);
        const float mid = a.value("k");
        WOKE_CHECK_NEAR(mid, 0.03125, kEps); // ease_in_out_quart(0.25)

        // Re-requesting the target already being animated to must not restart
        // the motion — this is what stops a per-frame call site (the window
        // open/close tween) from pinning its channel at t = 0 forever.
        WOKE_CHECK_NEAR(a.tween("k", 999.0f, 1.0f, 1.0f), mid, kEps);
        a.update(0.75f);
        WOKE_CHECK_NEAR(a.value("k"), 1.0, kEps);

        // A genuinely different target does restart, from the given origin.
        WOKE_CHECK_NEAR(a.tween("k", 0.25f, 0.75f, 1.0f), 0.25, kEps);
        WOKE_CHECK(a.active("k"));
        a.update(1.0f);
        WOKE_CHECK_NEAR(a.value("k"), 0.75, kEps);
    }

    // -- damp ----------------------------------------------------------------
    WOKE_SUITE("AnimationController::damp");
    {
        AnimationController a;

        // A channel that does not exist yet starts *at* the target, so the
        // first frame a widget appears it already reads its current hover
        // state instead of fading in from zero.
        WOKE_CHECK_NEAR(a.damp("d", 0.75f, 0.05f, 0.016f), 0.75, kEps);
        WOKE_CHECK_NEAR(a.value("d", -1.0f), 0.75, kEps);

        // Stepping happens in update() only: damp() alone must not integrate.
        WOKE_CHECK_NEAR(a.damp("d", 1.0f, 0.05f, 0.016f), 0.75, kEps);

        // Repeated damp()+update() converges on the target, then parks so an
        // idle channel costs nothing.
        const float dt = 1.0f / 60.0f;
        for (int i = 0; i < 60; ++i) {
            a.damp("d", 1.0f, 0.05f, dt);
            a.update(dt);
        }
        WOKE_CHECK_NEAR(a.value("d"), 1.0, kEps);
        WOKE_CHECK_FALSE(a.active("d"));
    }

    // -- motion scale --------------------------------------------------------
    WOKE_SUITE("AnimationController::motion_scale");
    {
        AnimationController a;
        WOKE_CHECK_NEAR(a.motion_scale(), 1.0, kEps);
        WOKE_CHECK_NEAR(a.scaled(0.220f), 0.220, kEps);

        // "Reduce Motion" compresses every duration through scaled().
        a.set_motion_scale(0.35f);
        WOKE_CHECK_NEAR(a.motion_scale(), 0.35, kEps);
        WOKE_CHECK_NEAR(a.scaled(1.0f), 0.35, kEps);

        // A tween is written with its designed duration but completes in the
        // scaled one.
        a.tween("t", 0.0f, 1.0f, 1.0f);
        a.update(0.35f);
        WOKE_CHECK_NEAR(a.value("t"), 1.0, kEps);
        WOKE_CHECK_FALSE(a.active("t"));

        // Out-of-range values degrade to "very fast" (never to a frozen UI or
        // a divide-by-zero); >1.0 is not a supported slow-motion mode.
        a.set_motion_scale(0.0f);
        WOKE_CHECK_NEAR(a.motion_scale(), 0.05, kEps);
        a.set_motion_scale(-4.0f);
        WOKE_CHECK_NEAR(a.motion_scale(), 0.05, kEps);
        a.set_motion_scale(1.0f);
        WOKE_CHECK_NEAR(a.motion_scale(), 1.0, kEps);
        a.set_motion_scale(4.0f);
        WOKE_CHECK_NEAR(a.motion_scale(), 1.0, kEps);
    }

    // -- key handling and the fixed pool -------------------------------------
    WOKE_SUITE("AnimationController::channels");
    {
        AnimationController a;

        // Keys are copied at claim time, so a stack buffer is safe to reuse.
        char scratch[16];
        make_key(scratch, static_cast<int>(sizeof(scratch)), 7);
        a.tween(scratch, 2.0f, 5.0f, 1.0f);
        make_key(scratch, static_cast<int>(sizeof(scratch)), 8);
        a.damp(scratch, 3.0f, 0.05f, 0.016f);
        WOKE_CHECK_NEAR(a.value("k7", -1.0f), 2.0, kEps);
        WOKE_CHECK_NEAR(a.value("k8", -1.0f), 3.0, kEps);

        // Empty/null keys are accepted and share one anonymous channel rather
        // than writing through a null pointer.
        a.tween("", 4.0f, 9.0f, 1.0f);
        WOKE_CHECK_NEAR(a.value("?", -1.0f), 4.0, kEps);
        WOKE_CHECK_NEAR(a.value(nullptr, -1.0f), -1.0, kEps);

        // A key longer than the channel's 24-byte field is truncated on store
        // and truncated the same way on lookup, so it still addresses itself.
        const char *long_key = "a-key-that-is-far-too-long-to-fit-in-a-channel";
        a.tween(long_key, 6.0f, 7.0f, 1.0f);
        WOKE_CHECK_NEAR(a.value(long_key, -1.0f), 6.0, kEps);

        WOKE_CHECK_INT(static_cast<int>(a.active_count()), 4);

        a.reset();
        WOKE_CHECK_INT(static_cast<int>(a.active_count()), 0);
        WOKE_CHECK_NEAR(a.value("k7", -1.0f), -1.0, kEps);
        WOKE_CHECK_FALSE(a.active("k7"));
    }

    WOKE_SUITE("AnimationController::pool exhaustion");
    {
        AnimationController a;
        char key[16];
        for (size_t i = 0; i < AnimationController::kMaxChannels; ++i) {
            make_key(key, static_cast<int>(sizeof(key)), static_cast<int>(i));
            a.tween(key, 0.0f, 1.0f, 1.0f);
        }
        WOKE_CHECK_INT(static_cast<int>(a.active_count()),
                       static_cast<int>(AnimationController::kMaxChannels));

        // One channel too many recycles the oldest slot: a widget silently
        // shares a channel (visually a stuck animation), never memory unsafety
        // and never an allocation.
        a.tween("overflow", 0.0f, 1.0f, 1.0f);
        WOKE_CHECK(a.active_count() <= AnimationController::kMaxChannels);
        WOKE_CHECK_NEAR(a.value("overflow", -1.0f), 0.0, kEps);
        WOKE_CHECK_NEAR(a.value("k0", -1.0f), -1.0, kEps); // slot 0 was reused

        // The pool still advances normally afterwards.
        a.update(1.0f);
        WOKE_CHECK_NEAR(a.value("overflow"), 1.0, kEps);
    }
}
