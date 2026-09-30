package wtf.woke.lite.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import wtf.woke.lite.core.Setting;

/**
 * The list an enum setting opens in the config screen.
 *
 * <p>Separate from the pane because it is drawn over the rows beneath it and
 * because it consumes the clicks meant for it before the pane sees them. Keeping
 * that in one place is what stops a click from both choosing a value and flipping
 * whatever row happened to be underneath it.</p>
 *
 * <p>It places itself from the {@link PaneLayout} it is given, so it follows the
 * row it belongs to when the list is scrolled, and flips above the row rather
 * than off the bottom of the pane.</p>
 */
final class DropdownOverlay {

    /** Height of one entry in the list. */
    private static final int ENTRY_HEIGHT = 12;

    /** Widest the list gets, however wide the pane is. */
    private static final int MAX_WIDTH = 150;

    /** Fastened to the pane's edge rather than allowed to hang outside it. */
    private static final int EDGE_MARGIN = 2;

    private ConfigRows.Placed placed;

    /** @return whether a list is open right now */
    boolean isOpen() {
        return placed != null;
    }

    /** @return whether the open list belongs to the row with this key */
    boolean isOpenFor(String key) {
        return placed != null && placed.row().key().equals(key);
    }

    void open(ConfigRows.Placed row) {
        this.placed = row;
    }

    /** @return whether an open list was closed, so ESC can be offered it first */
    boolean close() {
        if (placed == null) {
            return false;
        }
        placed = null;
        return true;
    }

    /** Draws the open list, marking the value in effect and whatever is hovered. */
    void render(DrawContext context, TextRenderer renderer, PaneLayout pane, double scroll,
            double mouseX, double mouseY) {
        ConfigRows.Placed row = placed;
        if (row == null) {
            return;
        }
        Box box = box(pane, scroll, row);
        ConfigTheme.framed(context, box.x(), box.y(), box.width(), box.height(), ConfigTheme.PANEL, ConfigTheme.ACCENT);
        Enum<?>[] values = values(row);
        Object current = setting(row).get();
        for (int index = 0; index < values.length; index++) {
            int rowY = box.y() + EDGE_MARGIN + index * ENTRY_HEIGHT;
            if (inside(mouseX, mouseY, box.x(), box.width(), rowY)) {
                ConfigTheme.fill(context, box.x() + 1, rowY, box.width() - 2, ENTRY_HEIGHT, ConfigTheme.ROW_HOVER);
            }
            context.drawTextWithShadow(renderer, Text.literal(SettingValueWidget.enumLabel(values[index])),
                    box.x() + 6, rowY + EDGE_MARGIN, values[index].equals(current) ? ConfigTheme.ACCENT : ConfigTheme.TEXT);
        }
    }

    /**
     * @return whether it chose a value; a click anywhere else still closes the
     *         list and is swallowed, so it never reaches a row underneath
     */
    boolean click(PaneLayout pane, double scroll, double mouseX, double mouseY) {
        ConfigRows.Placed row = placed;
        if (row == null) {
            return false;
        }
        Box box = box(pane, scroll, row);
        Enum<?>[] values = values(row);
        for (int index = 0; index < values.length; index++) {
            int rowY = box.y() + EDGE_MARGIN + index * ENTRY_HEIGHT;
            if (inside(mouseX, mouseY, box.x(), box.width(), rowY)) {
                store(setting(row), values[index]);
                placed = null;
                return true;
            }
        }
        placed = null;
        return true;
    }

    private static Box box(PaneLayout pane, double scroll, ConfigRows.Placed row) {
        int width = Math.min(MAX_WIDTH, pane.rowWidth());
        int height = values(row).length * ENTRY_HEIGHT + 2 * EDGE_MARGIN;
        int centred = pane.rowX() + (pane.rowWidth() - width) / 2;
        int x = Math.max(pane.x() + EDGE_MARGIN,
                Math.min(centred, pane.x() + pane.width() - width - EDGE_MARGIN));
        int rowTop = pane.rowY(row, scroll);
        int below = rowTop + row.height();
        int y = below + height > pane.bottom() - EDGE_MARGIN
                ? Math.max(pane.y() + EDGE_MARGIN, rowTop - height)
                : below;
        return new Box(x, y, width, height);
    }

    /** A rectangle on screen. */
    private record Box(int x, int y, int width, int height) {
    }

    private static Setting<?> setting(ConfigRows.Placed row) {
        return ((ConfigRow.SettingRow) row.row()).setting();
    }

    private static Enum<?>[] values(ConfigRows.Placed row) {
        return setting(row).enumConstants();
    }

    private static boolean inside(double mouseX, double mouseY, int x, int width, int rowY) {
        return mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ENTRY_HEIGHT;
    }

    @SuppressWarnings("unchecked")
    private static void store(Setting<?> setting, Object value) {
        ((Setting<Object>) setting).set(value);
    }
}
