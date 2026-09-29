package wtf.woke.lite.hud;

import java.util.List;
import java.util.Objects;

/**
 * The shape of a custom crosshair, expressed as integer rectangles in GUI units.
 *
 * <p>Everything is relative to the <em>centre unit</em>: the single GUI unit at
 * the screen centre, i.e. the unit the vanilla crosshair sits in. That keeps the
 * geometry independent of window size and GUI scale, and makes the shape
 * symmetric about the centre unit for odd thicknesses: {@code gap} counts the
 * empty units between the centre unit and the start of each arm, so {@code 0}
 * leaves only the centre unit empty — the hole vanilla's own crosshair has.</p>
 *
 * <p>Values are validated instead of clamped: a settings-clamped value and a
 * hard-coded one should be equally trustworthy by the time they reach a draw
 * call.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 *
 * @param style     which crosshair to draw
 * @param thickness arm thickness in GUI units, at least 1
 * @param gap       empty units between the centre unit and each arm
 * @param length    arm length in GUI units, at least 1
 */
public record CrosshairGeometry(CrosshairStyle style, int thickness, int gap, int length) {

    /** Largest arm thickness in GUI units. */
    public static final int MAX_THICKNESS = 8;

    /** Largest gap around the centre unit, in GUI units. */
    public static final int MAX_GAP = 16;

    /** Largest arm length in GUI units. */
    public static final int MAX_LENGTH = 32;

    /** Thickness a fresh install starts with: one GUI unit, like vanilla. */
    public static final int DEFAULT_THICKNESS = 1;

    /** Gap a fresh install starts with: a one-unit hole, like vanilla. */
    public static final int DEFAULT_GAP = 1;

    /** Arm length a fresh install starts with. */
    public static final int DEFAULT_LENGTH = 4;

    public CrosshairGeometry {
        Objects.requireNonNull(style, "style");
        requireRange(thickness, 1, MAX_THICKNESS, "thickness");
        requireRange(gap, 0, MAX_GAP, "gap");
        requireRange(length, 1, MAX_LENGTH, "length");
    }

    /** @return the crosshair a fresh install starts with */
    public static CrosshairGeometry defaults() {
        return new CrosshairGeometry(CrosshairStyle.CROSS, DEFAULT_THICKNESS, DEFAULT_GAP, DEFAULT_LENGTH);
    }

    /** @return {@code true} when this geometry paints anything at all */
    public boolean isVisible() {
        return style != CrosshairStyle.HIDDEN;
    }

    /**
     * Resolves the rectangles to paint, in absolute GUI coordinates.
     *
     * <p>For {@link CrosshairStyle#CROSS} the order is top, bottom, left, right —
     * the order a reader expects, and the order a test can assert on. A thick
     * crosshair with a small gap produces arms that overlap near the centre;
     * filling the same pixel twice is invisible, so the painted shape stays a
     * plus either way, and the centre unit itself is never painted.</p>
     *
     * @param centerX x coordinate of the centre unit
     * @param centerY y coordinate of the centre unit
     * @return the rectangles to fill, or an empty list when the crosshair is
     *         hidden
     */
    public List<HudBounds> arms(int centerX, int centerY) {
        return switch (style) {
            case HIDDEN -> List.of();
            case DOT -> List.of(square(centerX, centerY));
            case CROSS -> cross(centerX, centerY);
        };
    }

    private HudBounds square(int centerX, int centerY) {
        int half = thickness / 2;
        return HudBounds.at(centerX - half, centerY - half, thickness, thickness);
    }

    private List<HudBounds> cross(int centerX, int centerY) {
        int half = thickness / 2;
        int nearSide = gap;
        int farSide = gap + 1;
        HudBounds top = HudBounds.at(centerX - half, centerY - nearSide - length, thickness, length);
        HudBounds bottom = HudBounds.at(centerX - half, centerY + farSide, thickness, length);
        HudBounds left = HudBounds.at(centerX - nearSide - length, centerY - half, length, thickness);
        HudBounds right = HudBounds.at(centerX + farSide, centerY - half, length, thickness);
        return List.of(top, bottom, left, right);
    }

    private static void requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in [" + min + ", " + max + "], got " + value);
        }
    }
}
