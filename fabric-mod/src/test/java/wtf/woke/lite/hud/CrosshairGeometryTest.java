package wtf.woke.lite.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the crosshair shape down to exact rectangles.
 *
 * <p>These are the numbers a pixel-verification pass will compare against, so
 * they are asserted literally rather than derived from the implementation.
 */
class CrosshairGeometryTest {

    private static final int CENTER_X = 100;
    private static final int CENTER_Y = 50;

    @Test
    void hiddenPaintsNothing() {
        CrosshairGeometry geometry = new CrosshairGeometry(CrosshairStyle.HIDDEN, 1, 1, 4);

        assertFalse(geometry.isVisible());
        assertTrue(geometry.arms(CENTER_X, CENTER_Y).isEmpty());
    }

    @Test
    void dotIsASquareOnTheCentreUnit() {
        CrosshairGeometry thin = new CrosshairGeometry(CrosshairStyle.DOT, 1, 0, 1);
        assertTrue(thin.isVisible());
        assertEquals(List.of(HudBounds.at(100, 50, 1, 1)), thin.arms(CENTER_X, CENTER_Y));

        CrosshairGeometry thick = new CrosshairGeometry(CrosshairStyle.DOT, 3, 0, 1);
        assertEquals(List.of(HudBounds.at(99, 49, 3, 3)), thick.arms(CENTER_X, CENTER_Y),
                "an odd thickness is centred on the centre unit");
    }

    @Test
    void crossHasFourArmsInTopBottomLeftRightOrder() {
        CrosshairGeometry geometry = new CrosshairGeometry(CrosshairStyle.CROSS, 1, 1, 4);

        assertEquals(List.of(
                HudBounds.at(100, 45, 1, 4), // top:    rows 45..48
                HudBounds.at(100, 52, 1, 4), // bottom: rows 52..55
                HudBounds.at(95, 50, 4, 1),  // left:   cols 95..98
                HudBounds.at(102, 50, 4, 1)  // right:  cols 102..105
        ), geometry.arms(CENTER_X, CENTER_Y));
    }

    @Test
    void gapCountsTheEmptyUnitsAroundTheCentre() {
        CrosshairGeometry touching = new CrosshairGeometry(CrosshairStyle.CROSS, 1, 0, 3);
        CrosshairGeometry spaced = new CrosshairGeometry(CrosshairStyle.CROSS, 1, 2, 3);

        assertEquals(List.of(
                HudBounds.at(100, 47, 1, 3),
                HudBounds.at(100, 51, 1, 3),
                HudBounds.at(97, 50, 3, 1),
                HudBounds.at(101, 50, 3, 1)
        ), touching.arms(CENTER_X, CENTER_Y));

        List<HudBounds> spacedArms = spaced.arms(CENTER_X, CENTER_Y);
        assertEquals(HudBounds.at(100, 45, 1, 3), spacedArms.get(0), "two extra units move the top arm");
        assertEquals(HudBounds.at(100, 53, 1, 3), spacedArms.get(1));
        assertEquals(HudBounds.at(95, 50, 3, 1), spacedArms.get(2));
        assertEquals(HudBounds.at(103, 50, 3, 1), spacedArms.get(3));
    }

    @Test
    void thicknessWidensTheArmsWithoutMovingTheCentre() {
        CrosshairGeometry geometry = new CrosshairGeometry(CrosshairStyle.CROSS, 3, 1, 4);

        assertEquals(List.of(
                HudBounds.at(99, 45, 3, 4),
                HudBounds.at(99, 52, 3, 4),
                HudBounds.at(95, 49, 4, 3),
                HudBounds.at(102, 49, 4, 3)
        ), geometry.arms(CENTER_X, CENTER_Y));
    }

    @Test
    void aCrossNeverPaintsItsCentreUnit() {
        for (int thickness = 1; thickness <= CrosshairGeometry.MAX_THICKNESS; thickness++) {
            for (int gap = 0; gap <= 4; gap++) {
                List<HudBounds> arms = new CrosshairGeometry(CrosshairStyle.CROSS, thickness, gap, 6)
                        .arms(CENTER_X, CENTER_Y);

                assertFalse(covers(arms, CENTER_X, CENTER_Y),
                        "thickness " + thickness + ", gap " + gap + " filled the centre unit");
            }
        }
    }

    @Test
    void oddThicknessIsMirroredAboutTheCentreUnit() {
        for (int thickness = 1; thickness <= 5; thickness += 2) {
            for (int gap = 0; gap <= 3; gap++) {
                List<HudBounds> arms = new CrosshairGeometry(CrosshairStyle.CROSS, thickness, gap, 5)
                        .arms(CENTER_X, CENTER_Y);

                for (HudBounds arm : arms) {
                    assertTrue(arms.contains(mirrorX(arm)), "missing horizontal mirror of " + arm);
                    assertTrue(arms.contains(mirrorY(arm)), "missing vertical mirror of " + arm);
                }
            }
        }
    }

    @Test
    void defaultsAreTheDocumentedCross() {
        CrosshairGeometry defaults = CrosshairGeometry.defaults();

        assertEquals(CrosshairStyle.CROSS, defaults.style());
        assertEquals(CrosshairGeometry.DEFAULT_THICKNESS, defaults.thickness());
        assertEquals(CrosshairGeometry.DEFAULT_GAP, defaults.gap());
        assertEquals(CrosshairGeometry.DEFAULT_LENGTH, defaults.length());
        assertEquals(defaults, new CrosshairGeometry(CrosshairStyle.CROSS, 1, 1, 4));
    }

    @Test
    void aCentreAtTheScreenOriginIsStillValid() {
        List<HudBounds> arms = CrosshairGeometry.defaults().arms(0, 0);

        assertEquals(4, arms.size(), "negative coordinates are legal rectangles");
        assertEquals(HudBounds.at(0, -5, 1, 4), arms.get(0));
    }

    @Test
    void outOfRangeValuesAreRejectedRatherThanClamped() {
        assertThrows(NullPointerException.class, () -> new CrosshairGeometry(null, 1, 1, 4),
                "a null style is a programming error, not an out-of-range value");
        assertThrows(IllegalArgumentException.class, () -> new CrosshairGeometry(CrosshairStyle.CROSS, 0, 1, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new CrosshairGeometry(CrosshairStyle.CROSS, CrosshairGeometry.MAX_THICKNESS + 1, 1, 4));
        assertThrows(IllegalArgumentException.class, () -> new CrosshairGeometry(CrosshairStyle.CROSS, 1, -1, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new CrosshairGeometry(CrosshairStyle.CROSS, 1, CrosshairGeometry.MAX_GAP + 1, 4));
        assertThrows(IllegalArgumentException.class, () -> new CrosshairGeometry(CrosshairStyle.CROSS, 1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CrosshairGeometry(CrosshairStyle.CROSS, 1, 1, CrosshairGeometry.MAX_LENGTH + 1));
    }

    @Test
    void everyStylePaintsExactlyWhenItIsVisible() {
        assertEquals(3, CrosshairStyle.values().length,
                "a new style needs arm geometry, a label and a test here");

        for (CrosshairStyle style : CrosshairStyle.values()) {
            CrosshairGeometry geometry = new CrosshairGeometry(style, 1, 1, 4);
            assertEquals(style != CrosshairStyle.HIDDEN, geometry.isVisible(), style + " visibility");
            assertEquals(geometry.isVisible(), !geometry.arms(CENTER_X, CENTER_Y).isEmpty(),
                    style + " must paint something exactly when it is visible");
        }
    }

    private static boolean covers(List<HudBounds> arms, int x, int y) {
        return arms.stream().anyMatch(arm -> arm.contains(x, y));
    }

    /** @return the pixel set of {@code arm} reflected across the centre column */
    private static HudBounds mirrorX(HudBounds arm) {
        return HudBounds.at(2 * CENTER_X - arm.right() + 1, arm.y(), arm.width(), arm.height());
    }

    /** @return the pixel set of {@code arm} reflected across the centre row */
    private static HudBounds mirrorY(HudBounds arm) {
        return HudBounds.at(arm.x(), 2 * CENTER_Y - arm.bottom() + 1, arm.width(), arm.height());
    }
}
