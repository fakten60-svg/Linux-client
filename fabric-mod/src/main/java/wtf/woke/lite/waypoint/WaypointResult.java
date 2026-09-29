package wtf.woke.lite.waypoint;

import java.util.Locale;

/**
 * What came of editing the waypoint list.
 *
 * <p>A plain enum so the reason an edit was refused can be decided, translated
 * and tested without a game: the module turns its state into one of these, and
 * the command turns one of these into a message.</p>
 */
public enum WaypointResult {

    /** The waypoint was stored. */
    ADDED,

    /** The waypoint was dropped from the current world. */
    REMOVED,

    /** The name is blank, too long, or contains the storage separator. */
    INVALID_NAME,

    /** That name is already taken in the current world. */
    DUPLICATE,

    /** No waypoint of that name exists in the current world. */
    NOT_FOUND,

    /** The list already holds as many waypoints as it may. */
    FULL,

    /** The waypoint's position cannot be stored in the space an entry has. */
    TOO_LONG,

    /** No world is loaded, so there is nothing to name a position in. */
    NO_WORLD;

    /** @return {@code true} when the list actually changed */
    public boolean isSuccess() {
        return this == ADDED || this == REMOVED;
    }

    /** @return the translation key for this outcome */
    public String translationKey() {
        return "wokewtf.lite.waypoint_result." + name().toLowerCase(Locale.ROOT);
    }
}
