package wtf.woke.lite.inventory;

import java.util.Locale;

/**
 * What came of asking for an inventory sort.
 *
 * <p>A plain enum so the answer can be decided, translated and tested without a
 * game: the module turns its state into one of these, and the command turns one
 * of these into a message.</p>
 */
public enum SortResult {

    /** A plan was built and is now being replayed. */
    STARTED,

    /** Every stack is already where the chosen order wants it. */
    ALREADY_SORTED,

    /** Another sort is still running. */
    BUSY,

    /** The module is switched off, so nothing would replay the plan. */
    DISABLED,

    /** A screen is open, or the player is not in a world yet. */
    NOT_READY,

    /** The world is not one this client owns; see the module's reason key. */
    UNAVAILABLE;

    /**
     * @return {@code true} when the request was understood and nothing went
     *         wrong, including the case where there was simply nothing to do
     */
    public boolean isSuccess() {
        return this == STARTED || this == ALREADY_SORTED;
    }

    /** @return the translation key for this outcome */
    public String translationKey() {
        return "wokewtf.lite.sort_result." + name().toLowerCase(Locale.ROOT);
    }
}
