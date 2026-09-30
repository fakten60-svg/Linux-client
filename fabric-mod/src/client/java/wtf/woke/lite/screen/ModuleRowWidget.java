package wtf.woke.lite.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Draws one module header in the config screen's right pane: its name, one line
 * of description, and the switch that turns the module on.
 *
 * <p>Stateless on purpose. A module row has nothing of its own to remember — the
 * state it shows is the module's, read fresh through the row it is handed — so
 * these are static methods rather than objects, and the pane can draw a hundred
 * rows without allocating one of them.</p>
 *
 * <p>The switch is drawn rather than taken from a vanilla button so it belongs to
 * the same dark surface as the rest of the screen, and so an on module stands out
 * in a list of eleven of them. A module this build did not register has no
 * switch: that is the mod-level settings group, which is on by definition.</p>
 */
final class ModuleRowWidget {

    /** Width of the drawn on/off switch, and the gap to the pane's right edge. */
    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 10;
    private static final int SWITCH_MARGIN = 8;

    private ModuleRowWidget() {
        throw new AssertionError("No instances of " + ModuleRowWidget.class.getName());
    }

    /** Draws the row, assuming the caller has already clipped the pane. */
    static void render(DrawContext context, TextRenderer renderer, ConfigRow.ModuleRow row, boolean switchable,
            int x, int y, int width, boolean hovered) {
        int height = ConfigRow.MODULE_HEIGHT;
        ConfigTheme.fill(context, x, y, width, height, hovered ? ConfigTheme.PANEL_HOVER : ConfigTheme.PANEL);
        if (row.enabled()) {
            ConfigTheme.marker(context, x, y, height, ConfigTheme.ACCENT);
        }
        context.drawTextWithShadow(renderer, Text.literal(row.name()), x + 10, y + 2,
                row.enabled() ? ConfigTheme.ACCENT : ConfigTheme.TEXT);
        if (!row.description().isBlank()) {
            int room = width - 20 - (switchable ? SWITCH_WIDTH + SWITCH_MARGIN : 0);
            String text = renderer.trimToWidth(row.description(), Math.max(8, room));
            context.drawTextWithShadow(renderer, Text.literal(text), x + 10, y + 11, ConfigTheme.TEXT_MUTED);
        }
        if (switchable) {
            renderSwitch(context, row.enabled(), x, y, width);
        }
    }

    /** @return whether the pointer is on the switch, so the caller can flip it */
    static boolean switchHit(double mouseX, double mouseY, int x, int y, int width) {
        int switchX = switchX(x, width);
        int switchY = switchY(y);
        return mouseX >= switchX && mouseX < switchX + SWITCH_WIDTH
                && mouseY >= switchY && mouseY < switchY + SWITCH_HEIGHT;
    }

    private static void renderSwitch(DrawContext context, boolean on, int x, int y, int width) {
        int switchX = switchX(x, width);
        int switchY = switchY(y);
        ConfigTheme.framed(context, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT,
                on ? ConfigTheme.ACCENT_MUTED : ConfigTheme.BUTTON, ConfigTheme.BORDER);
        int knob = SWITCH_HEIGHT - 2;
        int knobX = on ? switchX + SWITCH_WIDTH - knob - 1 : switchX + 1;
        context.fill(knobX, switchY + 1, knobX + knob, switchY + 1 + knob,
                on ? ConfigTheme.ACCENT : ConfigTheme.TEXT_MUTED);
    }

    private static int switchX(int x, int width) {
        return x + width - SWITCH_WIDTH - SWITCH_MARGIN;
    }

    private static int switchY(int y) {
        return y + (ConfigRow.MODULE_HEIGHT - SWITCH_HEIGHT) / 2;
    }
}
