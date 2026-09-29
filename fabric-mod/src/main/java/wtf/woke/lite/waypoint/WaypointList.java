package wtf.woke.lite.waypoint;

import java.util.List;
import java.util.Objects;
import wtf.woke.lite.core.Setting;

/**
 * The stored waypoint list as the config holds it: encoded strings in one
 * setting, filed by world.
 *
 * <p>Every edit goes through here, so the rules — a usable name, one waypoint
 * per name and world, a bounded list, and an entry short enough to survive the
 * config's own truncation — are stated once. The caller supplies the world key
 * and the position, so nothing here needs a game and all of it can be checked
 * without one.</p>
 */
public final class WaypointList {

    /**
     * How many waypoints may be stored, across all worlds.
     *
     * <p>This is the limit the caller must also configure the setting with: the
     * setting truncates a longer list rather than reporting it, so a rule that
     * allowed more than the setting holds would lose waypoints silently.</p>
     */
    public static final int MAX_ENTRIES = 64;

    private WaypointList() {
        throw new AssertionError("No instances of " + WaypointList.class.getName());
    }

    /** @return every stored waypoint, of every world */
    public static List<Waypoint> all(Setting<List<String>> entries) {
        Objects.requireNonNull(entries, "entries");
        return Waypoints.decodeAll(entries.get());
    }

    /** @return the stored waypoints of one world, in the order they were added */
    public static List<Waypoint> inWorld(Setting<List<String>> entries, String worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        return Waypoints.inWorld(all(entries), worldKey);
    }

    /**
     * Stores one waypoint, unless a rule refuses it.
     *
     * @param entries  the setting holding the encoded list
     * @param worldKey the world the position belongs to
     * @param name     the name to store under
     * @return {@link WaypointResult#ADDED}, or the reason nothing was written
     */
    public static WaypointResult add(Setting<List<String>> entries, String worldKey, String name, int x, int y, int z) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(worldKey, "worldKey");
        if (!Waypoint.isValidName(name)) {
            return WaypointResult.INVALID_NAME;
        }
        List<Waypoint> waypoints = all(entries);
        if (Waypoints.byName(Waypoints.inWorld(waypoints, worldKey), name).isPresent()) {
            return WaypointResult.DUPLICATE;
        }
        if (waypoints.size() >= MAX_ENTRIES) {
            return WaypointResult.FULL;
        }
        Waypoint waypoint = new Waypoint(name, x, y, z, worldKey);
        if (waypoint.encodedLength() > Waypoint.MAX_ENCODED_LENGTH) {
            // The config truncates over-long entries, and a truncated entry is
            // an unreadable one, so refuse it while it can still be reported.
            return WaypointResult.TOO_LONG;
        }
        entries.set(Waypoints.encodeAll(Waypoints.add(waypoints, waypoint)));
        return WaypointResult.ADDED;
    }

    /**
     * Drops one waypoint of one world, leaving same-named ones elsewhere alone.
     *
     * @return {@link WaypointResult#REMOVED}, or the reason nothing was written
     */
    public static WaypointResult remove(Setting<List<String>> entries, String worldKey, String name) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(name, "name");
        List<Waypoint> waypoints = all(entries);
        if (Waypoints.byName(Waypoints.inWorld(waypoints, worldKey), name).isEmpty()) {
            return WaypointResult.NOT_FOUND;
        }
        entries.set(Waypoints.encodeAll(Waypoints.removeInWorld(waypoints, worldKey, name)));
        return WaypointResult.REMOVED;
    }
}
