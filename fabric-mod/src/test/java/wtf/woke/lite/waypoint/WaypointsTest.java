package wtf.woke.lite.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WaypointsTest {

    private static final String OVERWORLD = "sp:world@minecraft:overworld";
    private static final String NETHER = "sp:world@minecraft:the_nether";

    private static Waypoint at(String name, int x, int y, int z, String worldKey) {
        return new Waypoint(name, x, y, z, worldKey);
    }

    @Test
    void decodingSkipsEntriesItCannotUse() {
        List<String> entries = List.of(
                "home|10|64|20|" + OVERWORLD,
                "not a waypoint",
                "broken|xx|64|20|" + OVERWORLD,
                "mine|10|30|20|" + NETHER);

        List<Waypoint> decoded = Waypoints.decodeAll(entries);

        assertEquals(List.of("home", "mine"), decoded.stream().map(Waypoint::name).toList(),
                "a hand-edited file must never stop the other waypoints from loading");
    }

    @Test
    void encodingIsTheInverseOfDecoding() {
        List<Waypoint> waypoints = List.of(at("home", 10, 64, 20, OVERWORLD), at("mine", 1, 2, 3, NETHER));

        assertEquals(Waypoints.decodeAll(Waypoints.encodeAll(waypoints)), waypoints);
    }

    @Test
    void onlyTheCurrentWorldsWaypointsAreSelected() {
        List<Waypoint> waypoints = List.of(
                at("home", 0, 0, 0, OVERWORLD),
                at("mine", 0, 0, 0, NETHER),
                at("outpost", 0, 0, 0, OVERWORLD));

        assertEquals(List.of("home", "outpost"),
                Waypoints.inWorld(waypoints, OVERWORLD).stream().map(Waypoint::name).toList());
        assertTrue(Waypoints.inWorld(waypoints, "mp:elsewhere@minecraft:overworld").isEmpty());
    }

    @Test
    void nearestFirstOrdersByDistanceAndKeepsEverything() {
        List<Waypoint> waypoints = List.of(
                at("far", 100, 0, 0, OVERWORLD),
                at("near", 1, 0, 0, OVERWORLD),
                at("middle", 10, 0, 0, OVERWORLD));

        List<Waypoint> sorted = Waypoints.nearestFirst(waypoints, 0, 0, 0);

        assertEquals(List.of("near", "middle", "far"), sorted.stream().map(Waypoint::name).toList());
        assertEquals(3, sorted.size(), "sorting must not drop waypoints");
    }

    @Test
    void namesAreMatchedIgnoringCase() {
        List<Waypoint> waypoints = List.of(at("Home", 0, 0, 0, OVERWORLD));

        assertEquals("Home", Waypoints.byName(waypoints, "home").orElseThrow().name());
        assertTrue(Waypoints.byName(waypoints, "hut").isEmpty());
        assertThrows(NullPointerException.class, () -> Waypoints.byName(waypoints, null));
    }

    @Test
    void removalOnlyTouchesTheNamedWaypointOfOneWorld() {
        List<Waypoint> waypoints = List.of(
                at("home", 0, 0, 0, OVERWORLD),
                at("mine", 0, 0, 0, OVERWORLD),
                at("HOME", 0, 0, 0, NETHER));

        List<Waypoint> remaining = Waypoints.removeInWorld(waypoints, OVERWORLD, "home");

        assertEquals(2, remaining.size());
        assertFalse(remaining.contains(at("home", 0, 0, 0, OVERWORLD)));
        assertTrue(remaining.contains(at("HOME", 0, 0, 0, NETHER)),
                "a waypoint of another world must survive, even with the same name");
    }
}
