package wtf.woke.lite.waypoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Operations on a list of {@link Waypoint}s.
 *
 * <p>All of them are pure and return new lists: the config stores waypoints as
 * encoded strings, so the module reads a list, applies one of these, and writes
 * the result back. Keeping the list handling here means the module never
 * touches strings and this code is testable without a game.</p>
 */
public final class Waypoints {

    private Waypoints() {
        throw new AssertionError("No instances of " + Waypoints.class.getName());
    }

    /**
     * @param entries encoded waypoints, possibly hand-edited
     * @return every entry that decodes, in the order given
     */
    public static List<Waypoint> decodeAll(List<String> entries) {
        Objects.requireNonNull(entries, "entries");
        return entries.stream()
                .map(Waypoint::decode)
                .flatMap(Optional::stream)
                .toList();
    }

    /** @return the encoded form of every waypoint, ready for the config */
    public static List<String> encodeAll(List<Waypoint> waypoints) {
        Objects.requireNonNull(waypoints, "waypoints");
        return waypoints.stream().map(Waypoint::encode).toList();
    }

    /** @return the waypoints that belong to one world key, in the order given */
    public static List<Waypoint> inWorld(List<Waypoint> waypoints, String worldKey) {
        Objects.requireNonNull(worldKey, "worldKey");
        return waypoints.stream().filter(waypoint -> waypoint.worldKey().equals(worldKey)).toList();
    }

    /** @return the waypoints sorted by distance from a position, nearest first */
    public static List<Waypoint> nearestFirst(List<Waypoint> waypoints, double x, double y, double z) {
        return waypoints.stream()
                .sorted(Comparator.comparingLong(waypoint -> waypoint.distanceFrom(x, y, z)))
                .toList();
    }

    /** @return every waypoint plus one more, in the order given */
    public static List<Waypoint> add(List<Waypoint> waypoints, Waypoint waypoint) {
        Objects.requireNonNull(waypoints, "waypoints");
        Objects.requireNonNull(waypoint, "waypoint");
        List<Waypoint> extended = new ArrayList<>(waypoints);
        extended.add(waypoint);
        return List.copyOf(extended);
    }

    /** @return the waypoint with that name, ignoring case */
    public static Optional<Waypoint> byName(List<Waypoint> waypoints, String name) {
        Objects.requireNonNull(name, "name");
        return waypoints.stream().filter(waypoint -> waypoint.name().equalsIgnoreCase(name)).findFirst();
    }

    /**
     * @return every waypoint except the named one in that world, ignoring case;
     *         a same-named waypoint of another world is kept
     */
    public static List<Waypoint> removeInWorld(List<Waypoint> waypoints, String worldKey, String name) {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(name, "name");
        return waypoints.stream()
                .filter(waypoint -> !waypoint.worldKey().equals(worldKey) || !waypoint.name().equalsIgnoreCase(name))
                .toList();
    }
}
