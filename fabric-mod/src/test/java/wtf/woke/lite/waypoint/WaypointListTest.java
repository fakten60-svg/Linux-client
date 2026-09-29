package wtf.woke.lite.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;

class WaypointListTest {

    private static final String OVERWORLD = "sp:world@minecraft:overworld";
    private static final String NETHER = "sp:world@minecraft:the_nether";

    /** A setting configured the way the module configures it. */
    private static Setting<List<String>> storage() {
        return Settings.stringList("waypoints", List.of(), WaypointList.MAX_ENTRIES, Waypoint.MAX_ENCODED_LENGTH);
    }

    @Test
    void anAddedWaypointCanBeReadBackInItsOwnWorldOnly() {
        Setting<List<String>> entries = storage();

        assertEquals(WaypointResult.ADDED, WaypointList.add(entries, OVERWORLD, "home", 10, 64, -20));
        assertEquals(WaypointResult.ADDED, WaypointList.add(entries, NETHER, "mine", 1, 30, 2));

        assertEquals(List.of("home"), WaypointList.inWorld(entries, OVERWORLD).stream()
                .map(Waypoint::name).toList());
        assertEquals(List.of("mine"), WaypointList.inWorld(entries, NETHER).stream()
                .map(Waypoint::name).toList());
        assertEquals(2, WaypointList.all(entries).size(), "both worlds are stored side by side");
        assertEquals(new Waypoint("home", 10, 64, -20, OVERWORLD), WaypointList.all(entries).get(0));
    }

    @Test
    void aNameIsTakenOnlyWithinItsOwnWorld() {
        Setting<List<String>> entries = storage();
        WaypointList.add(entries, OVERWORLD, "home", 0, 0, 0);

        assertEquals(WaypointResult.DUPLICATE, WaypointList.add(entries, OVERWORLD, "HOME", 5, 5, 5),
                "one world may not have two waypoints of one name");
        assertEquals(WaypointResult.ADDED, WaypointList.add(entries, NETHER, "home", 5, 5, 5),
                "another world is a different place");
    }

    @Test
    void unusableNamesAndOverlongEntriesAreRefusedInsteadOfStored() {
        Setting<List<String>> entries = storage();

        assertEquals(WaypointResult.INVALID_NAME, WaypointList.add(entries, OVERWORLD, "", 0, 0, 0));
        assertEquals(WaypointResult.INVALID_NAME, WaypointList.add(entries, OVERWORLD, "bad|name", 0, 0, 0));
        assertEquals(WaypointResult.INVALID_NAME,
                WaypointList.add(entries, OVERWORLD, "n".repeat(Waypoint.MAX_NAME_LENGTH + 1), 0, 0, 0));
        assertEquals(WaypointResult.TOO_LONG,
                WaypointList.add(entries, "x".repeat(Waypoint.MAX_ENCODED_LENGTH), "home", 0, 0, 0));

        assertEquals(List.of(), WaypointList.all(entries), "a refused waypoint writes nothing");
    }

    @Test
    void aNameWithSpacesIsStorableEvenThoughTheCommandCannotTypeIt() {
        Setting<List<String>> entries = storage();

        assertEquals(WaypointResult.ADDED, WaypointList.add(entries, OVERWORLD, "north base", 0, 0, 0));
        assertEquals(List.of("north base"), WaypointList.all(entries).stream().map(Waypoint::name).toList());
    }

    @Test
    void theListFillsUpWithoutAnythingBeingDroppedOnTheWay() {
        Setting<List<String>> entries = storage();
        for (int index = 0; index < WaypointList.MAX_ENTRIES; index++) {
            assertEquals(WaypointResult.ADDED, WaypointList.add(entries, OVERWORLD, "wp" + index, 0, 0, 0),
                    "waypoint " + index + " must fit");
        }
        assertEquals(WaypointList.MAX_ENTRIES, WaypointList.all(entries).size(),
                "the setting must hold every waypoint the rules allowed");

        assertEquals(WaypointResult.FULL, WaypointList.add(entries, OVERWORLD, "one-too-many", 0, 0, 0));
        assertEquals(WaypointList.MAX_ENTRIES, WaypointList.all(entries).size(),
                "a refused waypoint must not push out one that was already stored");
    }

    @Test
    void removalTouchesOneWorldAndReportsWhatItDidNotFind() {
        Setting<List<String>> entries = storage();
        WaypointList.add(entries, OVERWORLD, "home", 0, 0, 0);
        WaypointList.add(entries, OVERWORLD, "mine", 0, 0, 0);
        WaypointList.add(entries, NETHER, "home", 0, 0, 0);

        assertEquals(WaypointResult.REMOVED, WaypointList.remove(entries, OVERWORLD, "home"));
        assertEquals(WaypointResult.NOT_FOUND, WaypointList.remove(entries, OVERWORLD, "home"));
        assertEquals(WaypointResult.NOT_FOUND, WaypointList.remove(entries, OVERWORLD, "elsewhere"));

        assertEquals(List.of("mine"), WaypointList.inWorld(entries, OVERWORLD).stream()
                .map(Waypoint::name).toList());
        assertEquals(1, WaypointList.inWorld(entries, NETHER).size(), "the other world keeps its waypoint");
    }

    @Test
    void readsHandBackACopyThatCannotBeEditedInPlace() {
        Setting<List<String>> entries = storage();
        WaypointList.add(entries, OVERWORLD, "home", 0, 0, 0);

        List<Waypoint> read = WaypointList.all(entries);

        assertThrows(UnsupportedOperationException.class, () -> read.add(new Waypoint("x", 0, 0, 0, OVERWORLD)));
        assertTrue(entries.get().size() == 1, "the setting still holds exactly what was written");
    }
}
