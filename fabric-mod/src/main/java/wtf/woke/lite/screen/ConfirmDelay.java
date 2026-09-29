package wtf.woke.lite.screen;

/**
 * A one-shot delay in ticks, used to slow a click that would otherwise be
 * instant.
 *
 * <p>Deliberately not a timer and not a loop: it is armed once, counts down in
 * the caller's ticks, and reports whether it is still holding something back.
 * The button it guards cannot respawn anybody by itself — it only refuses to be
 * pressed for a moment — so the worst case of a mistake here is a button that
 * is briefly unhelpful rather than a player who is suddenly alive again.</p>
 *
 * <p>No Minecraft types, so the counting is checked without a game.</p>
 */
public final class ConfirmDelay {

    /** Ticks in one second. */
    public static final int TICKS_PER_SECOND = 20;

    private int remainingTicks;

    /**
     * Arms the delay.
     *
     * @param seconds how long to hold; a value below one is treated as one, so
     *                an armed delay always does something
     */
    public void arm(int seconds) {
        remainingTicks = Math.max(1, seconds) * TICKS_PER_SECOND;
    }

    /** Disarms immediately. */
    public void clear() {
        remainingTicks = 0;
    }

    /** @return whether the delay is currently holding something back */
    public boolean isHolding() {
        return remainingTicks > 0;
    }

    /**
     * Advances by one tick.
     *
     * @return {@code true} on the tick the delay runs out, exactly once
     */
    public boolean tick() {
        if (remainingTicks <= 0) {
            return false;
        }
        remainingTicks--;
        return remainingTicks == 0;
    }

    /** @return whole seconds left, rounded up, zero when disarmed */
    public int remainingSeconds() {
        return (remainingTicks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }
}
