package wtf.woke.lite.hud;

import java.util.Locale;
import wtf.woke.lite.core.Labelled;

/**
 * Where a HUD element is pinned on screen, and which way its offsets move it.
 *
 * <p>Offsets always move the element <em>away from its anchor edge, inwards</em>:
 * a {@code TOP_RIGHT} element with {@code offsetX = 8} sits eight units in from
 * the right edge. One rule for all nine anchors, no per-anchor sign juggling at
 * the call site.</p>
 *
 * <p>Pure integer geometry with no Minecraft types, so it can be unit-tested
 * without a game.</p>
 */
public enum HudAnchor implements Labelled {

    TOP_LEFT(Alignment.START, Alignment.START),
    TOP_CENTER(Alignment.CENTER, Alignment.START),
    TOP_RIGHT(Alignment.END, Alignment.START),
    MIDDLE_LEFT(Alignment.START, Alignment.CENTER),
    CENTER(Alignment.CENTER, Alignment.CENTER),
    MIDDLE_RIGHT(Alignment.END, Alignment.CENTER),
    BOTTOM_LEFT(Alignment.START, Alignment.END),
    BOTTOM_CENTER(Alignment.CENTER, Alignment.END),
    BOTTOM_RIGHT(Alignment.END, Alignment.END);

    /** Which edge, or the middle, of an axis an element is pinned to. */
    public enum Alignment {
        START,
        CENTER,
        END
    }

    private final Alignment horizontal;
    private final Alignment vertical;

    HudAnchor(Alignment horizontal, Alignment vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    /** @return the translation key for this anchor's name in the config screen */
    @Override
    public String translationKey() {
        return "wokewtf.lite.anchor." + name().toLowerCase(Locale.ROOT);
    }

    public Alignment horizontal() {
        return horizontal;
    }

    public Alignment vertical() {
        return vertical;
    }

    /** @return this anchor's row: {@code 0} top, {@code 1} middle, {@code 2} bottom */
    public int row() {
        return vertical.ordinal();
    }

    /** @return this anchor's column: {@code 0} left, {@code 1} centre, {@code 2} right */
    public int column() {
        return horizontal.ordinal();
    }

    /**
     * Resolves the left edge of an element.
     *
     * @param screenWidth  usable width in GUI units
     * @param elementWidth element width in GUI units
     * @param offsetX      distance to pull the element inwards from its edge
     * @param margin       universal minimum distance from the screen border
     * @return the element's x coordinate, clamped into the screen
     */
    public int resolveX(int screenWidth, int elementWidth, int offsetX, int margin) {
        return clamp(switch (horizontal) {
            case START -> margin + offsetX;
            case CENTER -> (screenWidth - elementWidth) / 2 + offsetX;
            case END -> screenWidth - elementWidth - margin - offsetX;
        }, screenWidth, elementWidth);
    }

    /**
     * Resolves the top edge of an element; the vertical counterpart of
     * {@link #resolveX}.
     */
    public int resolveY(int screenHeight, int elementHeight, int offsetY, int margin) {
        return clamp(switch (vertical) {
            case START -> margin + offsetY;
            case CENTER -> (screenHeight - elementHeight) / 2 + offsetY;
            case END -> screenHeight - elementHeight - margin - offsetY;
        }, screenHeight, elementHeight);
    }

    /**
     * Keeps an element on screen: an offset large enough to push a small window
     * off the edge must not hide the element entirely.
     */
    private static int clamp(int coordinate, int available, int size) {
        int maximum = Math.max(0, available - size);
        return Math.max(0, Math.min(maximum, coordinate));
    }
}
