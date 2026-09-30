package wtf.woke.lite.screen;

import java.util.List;

/**
 * The geometry of the config screen's module pane.
 *
 * <p>A value rather than a set of offsets passed around, so the pane, the enum
 * list drawn over it and the rows inside it all measure a row the same way
 * instead of each deriving it again. Rebuilt whenever the rows change, which is
 * exactly when the offsets can change.</p>
 *
 * <p>The stacking itself comes from {@link ConfigRows#layout}, which is testable
 * without a game; this class only adds the pane's own position and the
 * screen-space conversions on top of it.</p>
 */
final class PaneLayout {

    private static final int TOP_PADDING = 4;
    private static final int SIDE_PADDING = 3;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final List<ConfigRows.Placed> placed;
    private final int contentHeight;

    /**
     * @param rows the rows to stack, top to bottom
     */
    PaneLayout(int x, int y, int width, int height, List<ConfigRow> rows) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.placed = ConfigRows.layout(rows, TOP_PADDING);
        this.contentHeight = ConfigRows.contentHeight(rows) + TOP_PADDING;
    }

    List<ConfigRows.Placed> placed() {
        return placed;
    }

    boolean isEmpty() {
        return placed.isEmpty();
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int bottom() {
        return y + height;
    }

    /** @return the left edge of the rows, inside the pane's border */
    int rowX() {
        return x + SIDE_PADDING;
    }

    /** @return the width of the rows, inside the pane's border */
    int rowWidth() {
        return width - 2 * SIDE_PADDING;
    }

    int contentHeight() {
        return contentHeight;
    }

    /** @return the row's top on screen, for a given scroll offset */
    int rowY(ConfigRows.Placed entry, double scroll) {
        return (int) (y + entry.y() - scroll);
    }

    /** @return how far the list can scroll; zero when all of it fits */
    double maxScroll() {
        return Math.max(0.0, contentHeight - height);
    }

    /** @return the row under the pointer, or {@code null} when there is none */
    ConfigRows.Placed entryAt(double mouseX, double mouseY, double scroll) {
        if (mouseX < rowX() || mouseX >= x + width) {
            return null;
        }
        double contentY = mouseY - y + scroll;
        for (ConfigRows.Placed entry : placed) {
            if (contentY >= entry.y() && contentY < entry.y() + entry.height()) {
                return entry;
            }
        }
        return null;
    }
}
