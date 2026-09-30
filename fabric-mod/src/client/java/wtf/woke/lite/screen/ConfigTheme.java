package wtf.woke.lite.screen;

import net.minecraft.client.gui.DrawContext;

/**
 * The colours and primitives the config screen is drawn with.
 *
 * <p>Every surface is a flat fill, which is the whole reason this is a small
 * class and not a renderer: the screen has to look like the rest of the mod —
 * the HUD's dark plates, not vanilla's grey widgets — and the cheapest way to
 * get there is to draw the plates ourselves and keep the widgets that measure,
 * hit-test and narrate. The two colours the HUD ships as its defaults are
 * repeated here so the screen and the overlay agree on sight.</p>
 *
 * <p>Alpha matters in a few of these: the backdrop is near-opaque so text stays
 * readable over a world, while the accent tints are translucent so a hover does
 * not flicker.</p>
 */
public final class ConfigTheme {

    /** Behind everything, over the blurred world. */
    public static final int BACKDROP = 0xF00B0E14;

    /** The header bar and the list pane. */
    public static final int PANEL = 0xFF111722;

    /** The module pane while the pointer is inside it. */
    public static final int PANEL_HOVER = 0xFF141B28;

    /** A one-pixel line between panes. */
    public static final int BORDER = 0xFF263041;

    /** A clickable bar that is not the row under the pointer. */
    public static final int BAR = 0xFF161D2A;

    /** The same bar while the pointer is on it. */
    public static final int BAR_HOVER = 0xFF1F2938;

    /** A setting row at rest. */
    public static final int ROW = 0xFF141A25;

    /** A setting row, or a category header, under the pointer. */
    public static final int ROW_HOVER = 0xFF1B2331;

    /** A small secondary button. */
    public static final int BUTTON = 0xFF1B2333;

    /** The same button under the pointer. */
    public static final int BUTTON_HOVER = 0xFF27324A;

    /** Primary text. Matches the HUD's shipped text colour. */
    public static final int TEXT = 0xFFE8EAED;

    /** Values and secondary labels. */
    public static final int TEXT_DIM = 0xFF98A2B3;

    /** A row that cannot be edited, and the search placeholder. */
    public static final int TEXT_MUTED = 0xFF6C7686;

    /** The mod's accent, for anything switched on. */
    public static final int ACCENT = 0xFF7BD4C8;

    /** The accent, muted, for the left edge of an open category. */
    public static final int ACCENT_MUTED = 0xFF35635E;

    private ConfigTheme() {
        throw new AssertionError("No instances of " + ConfigTheme.class.getName());
    }

    /** Fills a rectangle. */
    public static void fill(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + height, color);
    }

    /** Fills a rectangle and draws a one-pixel border just inside it. */
    public static void framed(DrawContext context, int x, int y, int width, int height, int fill, int border) {
        context.fill(x, y, x + width, y + height, border);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    /** Draws the three-pixel tab that marks a row as switched on. */
    public static void marker(DrawContext context, int x, int y, int height, int color) {
        context.fill(x, y, x + 3, y + height, color);
    }

    /** Draws a colour swatch with a border, so a light colour cannot vanish. */
    public static void swatch(DrawContext context, int x, int y, int size, int color) {
        framed(context, x, y, size, size, color | 0xFF000000, BORDER);
    }
}
