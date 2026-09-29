package wtf.woke.lite.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HudLayoutTest {

    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 240;

    @Test
    void defaultsToTheStandardMargin() {
        HudLayout layout = new HudLayout(HudAnchor.TOP_LEFT, 0, 0);
        assertEquals(HudLayout.DEFAULT_MARGIN, layout.margin());
    }

    @Test
    void placesAnElementAtItsAnchorPlusItsOffsets() {
        HudLayout layout = new HudLayout(HudAnchor.BOTTOM_RIGHT, 6, 2);
        HudBounds bounds = layout.bounds(40, 10, SCREEN_WIDTH, SCREEN_HEIGHT);

        assertEquals(270, bounds.x());
        assertEquals(224, bounds.y());
        assertEquals(40, bounds.width());
        assertEquals(10, bounds.height());
        assertEquals(310, bounds.right());
        assertEquals(234, bounds.bottom());
    }

    @Test
    void aCustomMarginMovesEveryEdgeInwards() {
        HudLayout layout = new HudLayout(HudAnchor.TOP_LEFT, 0, 0, 10);
        HudBounds bounds = layout.bounds(40, 10, SCREEN_WIDTH, SCREEN_HEIGHT);

        assertEquals(10, bounds.x());
        assertEquals(10, bounds.y());
    }

    @Test
    void derivationsAreNewInstancesAndKeepTheRest() {
        HudLayout layout = new HudLayout(HudAnchor.TOP_LEFT, 1, 2, 8);

        HudLayout moved = layout.withOffsets(3, 4);
        HudLayout reanchored = layout.withAnchor(HudAnchor.BOTTOM_RIGHT);

        assertNotSame(layout, moved);
        assertNotSame(layout, reanchored);
        assertEquals(1, layout.offsetX(), "the original is untouched");
        assertEquals(HudAnchor.TOP_LEFT, layout.anchor());
        assertEquals(3, moved.offsetX());
        assertEquals(4, moved.offsetY());
        assertEquals(8, moved.margin());
        assertEquals(HudAnchor.BOTTOM_RIGHT, reanchored.anchor());
        assertEquals(1, reanchored.offsetX());
    }

    @Test
    void negativeGeometryIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new HudLayout(HudAnchor.CENTER, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new HudLayout(HudAnchor.CENTER, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new HudLayout(HudAnchor.CENTER, 0, 0, -1));
    }

    @Test
    void placementIsClampedIntoTheScreen() {
        HudLayout layout = new HudLayout(HudAnchor.BOTTOM_RIGHT, 1000, 1000);
        HudBounds bounds = layout.bounds(40, 10, SCREEN_WIDTH, SCREEN_HEIGHT);

        assertTrue(bounds.x() >= 0 && bounds.right() <= SCREEN_WIDTH);
        assertTrue(bounds.y() >= 0 && bounds.bottom() <= SCREEN_HEIGHT);
    }

    @Test
    void theLayoutDescribesItselfForLogs() {
        assertEquals("TOP_RIGHT+(3,4) margin 4", new HudLayout(HudAnchor.TOP_RIGHT, 3, 4).toString());
    }
}
