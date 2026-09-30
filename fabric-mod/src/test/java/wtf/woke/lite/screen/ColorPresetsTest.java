package wtf.woke.lite.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

/**
 * Pins the colour cycle a click in the config screen walks through. The rule
 * that matters is that a click always changes the value, including for a colour
 * somebody typed into the config file by hand.
 */
class ColorPresetsTest {

    @Test
    void theCycleIsNotEmptyAndHasNoDuplicates() {
        assertTrue(ColorPresets.size() >= 4, "a cycle of three colours is not a palette");
        assertEquals(ColorPresets.all().size(), new HashSet<>(ColorPresets.all()).size());
    }

    @Test
    void cyclingWalksForwardAndWraps() {
        int first = ColorPresets.all().get(0);

        assertEquals(first, ColorPresets.next(ColorPresets.all().get(ColorPresets.size() - 1)));
        for (int index = 0; index < ColorPresets.size() - 1; index++) {
            assertEquals(ColorPresets.all().get(index + 1), ColorPresets.next(ColorPresets.all().get(index)));
        }
    }

    @Test
    void aColourOutsideThePaletteMovesToItsFirstEntry() {
        assertEquals(ColorPresets.all().get(0), ColorPresets.next(0xFF123456));
        assertEquals(ColorPresets.all().get(0), ColorPresets.next(0));
        assertFalse(ColorPresets.isPreset(0xFF123456));
    }

    @Test
    void theSameColourIsFoundAgain() {
        for (int preset : ColorPresets.all()) {
            int index = ColorPresets.indexOf(preset);
            assertTrue(index >= 0);
            assertEquals(preset, ColorPresets.all().get(index));
            assertTrue(ColorPresets.isPreset(preset));
        }
        assertEquals(-1, ColorPresets.indexOf(0xFF00FF00));
    }

    @Test
    void cyclingTwiceThroughThePaletteReturnsToTheStart() {
        int start = ColorPresets.all().get(0);
        int colour = start;
        for (int step = 0; step < ColorPresets.size(); step++) {
            colour = ColorPresets.next(colour);
        }

        assertEquals(start, colour);
        assertNotEquals(ColorPresets.all().get(1), start);
    }
}
