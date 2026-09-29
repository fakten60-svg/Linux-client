package wtf.woke.lite.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HudAnchorTest {

    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 240;
    private static final int MARGIN = 4;

    @Test
    void startAnchorsSitAgainstTheNearEdge() {
        assertEquals(4, HudAnchor.TOP_LEFT.resolveX(SCREEN_WIDTH, 40, 0, MARGIN));
        assertEquals(4, HudAnchor.TOP_LEFT.resolveY(SCREEN_HEIGHT, 10, 0, MARGIN));
    }

    @Test
    void endAnchorsSitAgainstTheFarEdge() {
        assertEquals(276, HudAnchor.TOP_RIGHT.resolveX(SCREEN_WIDTH, 40, 0, MARGIN));
        assertEquals(226, HudAnchor.BOTTOM_RIGHT.resolveY(SCREEN_HEIGHT, 10, 0, MARGIN));
    }

    @Test
    void centreAnchorsCentreTheElement() {
        assertEquals(140, HudAnchor.TOP_CENTER.resolveX(SCREEN_WIDTH, 40, 0, MARGIN));
        assertEquals(140, HudAnchor.CENTER.resolveX(SCREEN_WIDTH, 40, 0, MARGIN));
        assertEquals(115, HudAnchor.CENTER.resolveY(SCREEN_HEIGHT, 10, 0, MARGIN));
        assertEquals(115, HudAnchor.MIDDLE_LEFT.resolveY(SCREEN_HEIGHT, 10, 0, MARGIN));
    }

    @Test
    void offsetsAlwaysMoveAwayFromTheAnchorEdgeInwards() {
        assertEquals(12, HudAnchor.TOP_LEFT.resolveX(SCREEN_WIDTH, 40, 8, MARGIN));
        assertEquals(268, HudAnchor.TOP_RIGHT.resolveX(SCREEN_WIDTH, 40, 8, MARGIN));
        assertEquals(148, HudAnchor.TOP_CENTER.resolveX(SCREEN_WIDTH, 40, 8, MARGIN));
        assertEquals(218, HudAnchor.BOTTOM_RIGHT.resolveY(SCREEN_HEIGHT, 10, 8, MARGIN));
    }

    @Test
    void anOffsetLargeEnoughToLeaveTheScreenIsClampedInstead() {
        assertEquals(0, HudAnchor.TOP_RIGHT.resolveX(SCREEN_WIDTH, 40, 1000, MARGIN));
        assertEquals(0, HudAnchor.BOTTOM_RIGHT.resolveY(SCREEN_HEIGHT, 10, 1000, MARGIN));
    }

    @Test
    void anElementWiderThanTheScreenIsClampedToTheOrigin() {
        assertEquals(0, HudAnchor.TOP_LEFT.resolveX(SCREEN_WIDTH, 400, 0, MARGIN));
        assertEquals(0, HudAnchor.CENTER.resolveY(SCREEN_HEIGHT, 400, 0, MARGIN));
    }

    @Test
    void alignmentAndIndicesDescribeThePosition() {
        assertEquals(HudAnchor.Alignment.START, HudAnchor.TOP_LEFT.horizontal());
        assertEquals(HudAnchor.Alignment.CENTER, HudAnchor.CENTER.vertical());
        assertEquals(HudAnchor.Alignment.END, HudAnchor.BOTTOM_RIGHT.horizontal());

        assertEquals(0, HudAnchor.TOP_LEFT.row());
        assertEquals(0, HudAnchor.TOP_LEFT.column());
        assertEquals(1, HudAnchor.CENTER.row());
        assertEquals(1, HudAnchor.CENTER.column());
        assertEquals(2, HudAnchor.BOTTOM_RIGHT.row());
        assertEquals(2, HudAnchor.MIDDLE_RIGHT.column());
        assertEquals(1, HudAnchor.BOTTOM_CENTER.column(), "bottom centre is horizontally centred");
    }

    @Test
    void everyAnchorIsReachableAndResolvesInsideTheScreen() {
        for (HudAnchor anchor : HudAnchor.values()) {
            int x = anchor.resolveX(SCREEN_WIDTH, 40, 0, MARGIN);
            int y = anchor.resolveY(SCREEN_HEIGHT, 10, 0, MARGIN);
            assertEquals(true, x >= 0 && x + 40 <= SCREEN_WIDTH, anchor + " x out of range: " + x);
            assertEquals(true, y >= 0 && y + 10 <= SCREEN_HEIGHT, anchor + " y out of range: " + y);
        }
    }
}
