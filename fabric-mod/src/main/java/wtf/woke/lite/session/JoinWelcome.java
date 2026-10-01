package wtf.woke.lite.session;

/**
 * Decides when the one-time chat guide is printed.
 *
 * <p>The guide tells the player how to reach the settings screen, so it is worth
 * exactly one line and no more: printed on the first tick after the first world
 * join of a client run, and never again — not on a second join, not after a
 * reconnect. Keeping that decision here rather than as a bare flag in the event
 * wiring means the "only once, only on the first tick" rule is testable without
 * a game, which is the same reason every other rule in this mod lives in its own
 * class.</p>
 *
 * <p>{@link #arm()} is called on the join event, {@link #consume()} once per
 * tick; a join arms it and the tick that follows prints it.</p>
 */
public final class JoinWelcome {

    private boolean shown;
    private boolean pending;

    /** Arms the guide. A no-op once it has been shown; joins do not repeat it. */
    public void arm() {
        if (!shown) {
            pending = true;
        }
    }

    /** @return whether the guide should be printed now; true at most once ever */
    public boolean consume() {
        if (!pending) {
            return false;
        }
        pending = false;
        shown = true;
        return true;
    }

    /** @return whether the guide has been printed in this client run */
    public boolean hasShown() {
        return shown;
    }
}
