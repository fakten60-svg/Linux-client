package wtf.woke.lite.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class WaypointTest {

    private static final String WORLD = "sp:world@minecraft:overworld";

    @Test
    void encodeAndDecodeAreInverses() {
        Waypoint waypoint = new Waypoint("home", 120, 64, -30, WORLD);

        assertEquals("home|120|64|-30|" + WORLD, waypoint.encode());
        assertEquals(Optional.of(waypoint), Waypoint.decode(waypoint.encode()));
    }

    @Test
    void negativeAndLargeCoordinatesSurviveTheRoundTrip() {
        Waypoint waypoint = new Waypoint("far", -30000000, -64, 30000000, "mp:play.example.net@minecraft:the_nether");

        assertEquals(Optional.of(waypoint), Waypoint.decode(waypoint.encode()));
        assertEquals(-30000000, waypoint.x());
    }

    @Test
    void entriesThatAreNotWaypointsAreRejectedInsteadOfGuessed() {
        assertTrue(Waypoint.decode(null).isEmpty());
        assertTrue(Waypoint.decode("").isEmpty());
        assertTrue(Waypoint.decode("home|120|64").isEmpty(), "missing fields");
        assertTrue(Waypoint.decode("home|120|64|-30|world|extra").isEmpty(), "too many fields");
        assertTrue(Waypoint.decode("home|abc|64|-30|world").isEmpty(), "unparsable coordinate");
        assertTrue(Waypoint.decode("home|120|64|-30|").isEmpty(), "blank world key");
        assertTrue(Waypoint.decode("|120|64|-30|world").isEmpty(), "blank name");
    }

    @Test
    void namesMustBeStorable() {
        assertTrue(Waypoint.isValidName("home"));
        assertTrue(Waypoint.isValidName("base_2"));
        assertFalse(Waypoint.isValidName(null));
        assertFalse(Waypoint.isValidName("   "));
        assertFalse(Waypoint.isValidName("with" + Waypoint.SEPARATOR + "separator"));
        assertFalse(Waypoint.isValidName("x".repeat(Waypoint.MAX_NAME_LENGTH + 1)));

        assertThrows(IllegalArgumentException.class, () -> new Waypoint("bad|name", 0, 0, 0, WORLD));
        assertThrows(IllegalArgumentException.class, () -> new Waypoint("ok", 0, 0, 0, " "));
        assertThrows(NullPointerException.class, () -> new Waypoint(null, 0, 0, 0, WORLD));
    }

    @Test
    void theWorstCaseNameAndPositionStillFitTheStoredEntry() {
        // The config truncates an over-long entry instead of refusing it, and a
        // truncated entry is an unreadable one, so this is the ceiling the
        // module checks against before it writes anything.
        String worldKey = "mp:play.some-rather-long-server-address.example.net:25565@"
                + "some-modpack:the_deepest_dimension";
        Waypoint worstCase = new Waypoint("n".repeat(Waypoint.MAX_NAME_LENGTH),
                -30000000, -64, 30000000, worldKey);

        assertTrue(worstCase.encodedLength() <= Waypoint.MAX_ENCODED_LENGTH,
                "a storable waypoint must fit in " + Waypoint.MAX_ENCODED_LENGTH + " characters, as it takes "
                        + worstCase.encodedLength());
        assertEquals(Optional.of(worstCase), Waypoint.decode(worstCase.encode()));
    }

    @Test
    void distanceIsRoundedToWholeBlocks() {
        Waypoint waypoint = new Waypoint("spot", 3, 4, 0, WORLD);

        assertEquals(5, waypoint.distanceFrom(0, 0, 0), "3-4-0 is exactly 5 blocks away");
        assertEquals(0, waypoint.distanceFrom(3, 4, 0));
        assertEquals(5, waypoint.distanceFrom(0, 0, 0.4));
    }
}
