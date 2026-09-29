package wtf.woke.lite.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HudBoundsTest {

    @Test
    void edgesFollowFromPositionAndSize() {
        HudBounds bounds = HudBounds.at(10, 20, 30, 40);
        assertEquals(40, bounds.right());
        assertEquals(60, bounds.bottom());
    }

    @Test
    void containmentIsHalfOpen() {
        HudBounds bounds = HudBounds.at(10, 20, 30, 40);

        assertTrue(bounds.contains(10, 20), "top-left corner included");
        assertTrue(bounds.contains(39, 59), "last pixel included");
        assertFalse(bounds.contains(40, 59), "right edge excluded");
        assertFalse(bounds.contains(39, 60), "bottom edge excluded");
        assertFalse(bounds.contains(9, 20));
    }

    @Test
    void offsetsKeepTheSizeAndExpansionGrowsEverySide() {
        HudBounds bounds = HudBounds.at(10, 20, 30, 40);

        HudBounds moved = bounds.offset(5, -5);
        assertEquals(15, moved.x());
        assertEquals(15, moved.y());
        assertEquals(bounds.width(), moved.width());

        HudBounds padded = bounds.expanded(2);
        assertEquals(8, padded.x());
        assertEquals(18, padded.y());
        assertEquals(34, padded.width());
        assertEquals(44, padded.height());
    }

    @Test
    void emptyAndInvalidRectanglesAreHandled() {
        assertTrue(HudBounds.sized(0, 10).isEmpty());
        assertTrue(HudBounds.sized(10, 0).isEmpty());
        assertFalse(HudBounds.sized(1, 1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> HudBounds.at(0, 0, -1, 5));
        assertThrows(IllegalArgumentException.class, () -> HudBounds.at(0, 0, 5, -1));
    }
}
