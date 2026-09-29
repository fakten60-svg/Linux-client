package wtf.woke.lite.hud;

import java.util.Locale;

/**
 * Which crosshair the {@code ui.crosshair} module paints.
 *
 * <p>Cosmetic only: this decides what the client draws at the screen centre and
 * touches nothing else. {@link #HIDDEN} is the same kind of preference as
 * turning the HUD off — an unobstructed view, not an advantage.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 */
public enum CrosshairStyle {

    /** The classic four-arm cross. */
    CROSS,

    /** A single square on the centre of the screen. */
    DOT,

    /** Nothing at all. */
    HIDDEN;

    /**
     * @return the translation key for this constant's display label, e.g.
     *         {@code wokewtf.lite.crosshair_style.cross}
     */
    public String translationKey() {
        return "wokewtf.lite.crosshair_style." + name().toLowerCase(Locale.ROOT);
    }
}
