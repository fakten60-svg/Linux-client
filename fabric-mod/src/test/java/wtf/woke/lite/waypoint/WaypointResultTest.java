package wtf.woke.lite.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WaypointResultTest {

    @Test
    void onlyAnEditThatHappenedCountsAsSuccess() {
        assertTrue(WaypointResult.ADDED.isSuccess());
        assertTrue(WaypointResult.REMOVED.isSuccess());

        assertFalse(WaypointResult.INVALID_NAME.isSuccess());
        assertFalse(WaypointResult.DUPLICATE.isSuccess());
        assertFalse(WaypointResult.NOT_FOUND.isSuccess());
        assertFalse(WaypointResult.FULL.isSuccess());
        assertFalse(WaypointResult.TOO_LONG.isSuccess());
        assertFalse(WaypointResult.NO_WORLD.isSuccess());
    }

    @Test
    void everyConstantHasItsOwnTranslationKey() {
        long distinctKeys = Arrays.stream(WaypointResult.values())
                .map(WaypointResult::translationKey)
                .distinct()
                .count();

        assertEquals(WaypointResult.values().length, distinctKeys);
        assertEquals("wokewtf.lite.waypoint_result.added", WaypointResult.ADDED.translationKey());
        assertEquals("wokewtf.lite.waypoint_result.no_world", WaypointResult.NO_WORLD.translationKey());
    }
}
