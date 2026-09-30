package wtf.woke.lite.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Draws one setting row of the config screen's right pane: its label, its value
 * control and the small button that puts the value back to its default.
 *
 * <p>Stateless, like the module rows: a setting row holds nothing of its own, so
 * these are static methods and the pane draws a list of them without allocating
 * anything.</p>
 *
 * <p>The geometry every part of a row shares lives here — where the value column
 * starts, where a slider's track ends, where the reset button sits. Drawing and
 * hit-testing both read those helpers, so they cannot drift apart. The control
 * itself is in {@link SettingValueWidget}.</p>
 */
final class SettingRowWidget {

    /** Size of the reset button, and the gap between it and the pane's edge. */
    private static final int RESET_WIDTH = 34;
    private static final int RESET_HEIGHT = 12;
    private static final int RESET_MARGIN = 6;

    /** Narrowest value column, so a short label cannot squeeze the control flat. */
    private static final int VALUE_COLUMN_MIN = 56;

    /** Room reserved on the right of a slider for the number itself. */
    static final int VALUE_TEXT_WIDTH = 62;

    private SettingRowWidget() {
        throw new AssertionError("No instances of " + SettingRowWidget.class.getName());
    }

    /** Draws the whole row, assuming the caller has already clipped the pane. */
    static void render(DrawContext context, TextRenderer renderer, ConfigRow.SettingRow row,
            int x, int y, int width, double mouseX, double mouseY) {
        ConfigTheme.fill(context, x, y, width, ConfigRow.SETTING_HEIGHT,
                inside(mouseX, mouseY, x, y, width) ? ConfigTheme.ROW_HOVER : ConfigTheme.ROW);
        context.drawTextWithShadow(renderer, Text.literal(row.label()), x + 14, y + 4, ConfigTheme.TEXT_DIM);
        SettingValueWidget.render(context, renderer, row.setting(), x, y, width);
        renderReset(context, renderer, x, y, width, mouseX, mouseY);
    }

    /** @return whether the pointer is on the reset button */
    static boolean resetHit(double mouseX, double mouseY, int x, int y, int width) {
        int resetX = resetX(x, width);
        int resetY = resetY(y);
        return mouseX >= resetX && mouseX < resetX + RESET_WIDTH
                && mouseY >= resetY && mouseY < resetY + RESET_HEIGHT;
    }

    /**
     * @return whether the pointer is on the value control, so the caller can open
     *         the enum list or store a picked value
     */
    static boolean valueHit(double mouseX, double mouseY, int x, int y, int width) {
        return inside(mouseX, mouseY, x, y, width)
                && mouseX >= valueX(x, width)
                && !resetHit(mouseX, mouseY, x, y, width);
    }

    /** @return the tooltip the pointer should show over this row, or {@code null} for none */
    static Text tooltipAt(ConfigRow.SettingRow row, double mouseX, double mouseY, int x, int y, int width) {
        return tooltipFor(row, resetHit(mouseX, mouseY, x, y, width));
    }

    private static Text tooltipFor(ConfigRow.SettingRow row, boolean onReset) {
        if (onReset) {
            return Text.translatable("wokewtf.lite.config.reset.tooltip");
        }
        return row.hasTooltip() ? Text.literal(row.tooltip()) : null;
    }

    private static void renderReset(DrawContext context, TextRenderer renderer, int x, int y, int width,
            double mouseX, double mouseY) {
        int resetX = resetX(x, width);
        int resetY = resetY(y);
        boolean lit = mouseX >= resetX && mouseX < resetX + RESET_WIDTH
                && mouseY >= resetY && mouseY < resetY + RESET_HEIGHT;
        ConfigTheme.framed(context, resetX, resetY, RESET_WIDTH, RESET_HEIGHT,
                lit ? ConfigTheme.BUTTON_HOVER : ConfigTheme.BUTTON, ConfigTheme.BORDER);
        Text label = Text.translatable("wokewtf.lite.config.reset");
        context.drawTextWithShadow(renderer, label,
                resetX + (RESET_WIDTH - renderer.getWidth(label)) / 2, resetY + 2,
                lit ? ConfigTheme.TEXT : ConfigTheme.TEXT_DIM);
    }

    /** @return where the value column starts inside a row of this width */
    static int valueX(int x, int width) {
        return x + Math.max(VALUE_COLUMN_MIN, width / 2);
    }

    /** @return where a slider's track starts */
    static int trackX(int x, int width) {
        return valueX(x, width);
    }

    /** @return how wide a slider's track is, leaving room for the number and the button */
    static int trackWidth(int x, int width) {
        return Math.max(8, resetX(x, width) - 6 - VALUE_TEXT_WIDTH - 4 - trackX(x, width));
    }

    /** @return the width a row's reset button and its margin together occupy */
    static int resetWidth() {
        return RESET_WIDTH + RESET_MARGIN;
    }

    private static int resetX(int x, int width) {
        return x + width - RESET_WIDTH - RESET_MARGIN;
    }

    private static int resetY(int y) {
        return y + (ConfigRow.SETTING_HEIGHT - RESET_HEIGHT) / 2;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ConfigRow.SETTING_HEIGHT;
    }
}
