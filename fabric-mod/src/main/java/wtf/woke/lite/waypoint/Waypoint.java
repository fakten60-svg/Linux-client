package wtf.woke.lite.waypoint;

import java.util.Objects;
import java.util.Optional;
import wtf.woke.lite.core.Validator;

/**
 * One named position, remembered for one world.
 *
 * <p>The world key is part of the record on purpose: waypoints live in a single
 * list inside the config, and the key is what keeps another world's waypoints
 * out of the list rendered on screen.</p>
 *
 * <p>The encoded form ({@code name|x|y|z|worldKey}) is what the config stores.
 * It is deliberately not JSON: the value travels inside the config's own string
 * list, so it has to survive being a plain string, and a separator that cannot
 * appear in a name makes decoding unambiguous.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 *
 * @param name     the label the player gave the waypoint
 * @param x        block coordinate
 * @param y        block coordinate
 * @param z        block coordinate
 * @param worldKey which world and dimension this waypoint belongs to
 */
public record Waypoint(String name, int x, int y, int z, String worldKey) {

    /** Field separator in the encoded form. */
    public static final char SEPARATOR = '|';

    /** Longest accepted name, so one bad entry cannot flood the config. */
    public static final int MAX_NAME_LENGTH = 24;

    /**
     * Longest encoded waypoint the config will store.
     *
     * <p>Wide enough for the worst case the game can produce: a full-length
     * name, three coordinates at the edge of the world, and a multiplayer key
     * holding a long server address and a long dimension name. The config
     * truncates over-long entries instead of rejecting them, and a truncated
     * entry is an unreadable one, so anything longer must be refused before it
     * is stored rather than silently written and lost.</p>
     */
    public static final int MAX_ENCODED_LENGTH = 160;

    /** Fields in an encoded waypoint. */
    private static final int FIELD_COUNT = 5;

    public Waypoint {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(worldKey, "worldKey");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid waypoint name: '" + name + "'");
        }
        requireEncodable(worldKey, "worldKey");
    }

    /** Encodes this waypoint into its single-line config form. */
    public String encode() {
        return name + SEPARATOR + x + SEPARATOR + y + SEPARATOR + z + SEPARATOR + worldKey;
    }

    /**
     * @param entry one encoded waypoint
     * @return the decoded waypoint, or empty when the entry is not one — a
     *         hand-edited or older entry must never break startup
     */
    public static Optional<Waypoint> decode(String entry) {
        if (entry == null) {
            return Optional.empty();
        }
        String[] fields = entry.split("\\" + SEPARATOR, -1);
        if (fields.length != FIELD_COUNT) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Waypoint(fields[0], Integer.parseInt(fields[1].trim()),
                    Integer.parseInt(fields[2].trim()), Integer.parseInt(fields[3].trim()), fields[4]));
        } catch (IllegalArgumentException notAWaypoint) {
            return Optional.empty();
        }
    }

    /**
     * @param name the candidate name
     * @return whether the name can be stored and encoded again unchanged, and
     *         only carries characters the game itself would render
     */
    public static boolean isValidName(String name) {
        return name != null
                && !name.isBlank()
                && name.length() <= MAX_NAME_LENGTH
                && name.indexOf(SEPARATOR) < 0
                && Validator.isGameSafeText(name);
    }

    /** @return the encoded form's length, i.e. how much config space it takes */
    public int encodedLength() {
        return encode().length();
    }

    /** @return the rounded distance from a position to this waypoint, in blocks */
    public long distanceFrom(double positionX, double positionY, double positionZ) {
        double dx = x - positionX;
        double dy = y - positionY;
        double dz = z - positionZ;
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private static void requireEncodable(String value, String fieldName) {
        if (value.isBlank() || value.indexOf(SEPARATOR) >= 0) {
            throw new IllegalArgumentException(fieldName + " must not be blank or contain '" + SEPARATOR + "'");
        }
    }
}
