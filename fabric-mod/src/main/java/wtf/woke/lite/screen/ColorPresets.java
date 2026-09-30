package wtf.woke.lite.screen;

import java.util.List;

/**
 * The colours the config screen cycles a colour setting through.
 *
 * <p>Deliberately a short, fixed list rather than a picker. A full colour
 * picker is a screen of its own — hue strip, alpha slider, hex box — and none
 * of it would be verifiable in this environment, while almost every real change
 * a player wants is "make it a bit lighter" or "make it match the accent". The
 * exact ARGB value stays editable in {@code config/wokewtf-lite.json}, which is
 * how it was set before this screen existed.</p>
 *
 * <p>The palette itself is the one the mod already draws with, plus the HUD
 * defaults, so the first clicks land on colours the rest of the UI agrees
 * with.</p>
 */
public final class ColorPresets {

    /** Every colour the cycle visits, in order. */
    private static final List<Integer> PRESETS = List.of(
            0xF00B0E14,   // the screen's own backdrop: near-black
            0x8010141C,   // the shipped HUD plate
            0xFF111722,   // the panel body
            0xFF1D2635,   // a raised row
            0xFFE8EAED,   // the shipped HUD text colour
            0xFF7BD4C8,   // the screen accent
            0xFFFFC857,   // warm amber
            0xFFFF5F56,   // warning red
            0xFF5AC8FA,   // cool blue
            0x00000000);  // fully transparent

    private ColorPresets() {
        throw new AssertionError("No instances of " + ColorPresets.class.getName());
    }

    /** @return every preset, in cycle order */
    public static List<Integer> all() {
        return PRESETS;
    }

    public static int size() {
        return PRESETS.size();
    }

    /** @return the position of {@code color} in the cycle, or {@code -1} */
    public static int indexOf(int color) {
        return PRESETS.indexOf(color);
    }

    /** @return whether {@code color} is one of the presets */
    public static boolean isPreset(int color) {
        return PRESETS.contains(color);
    }

    /**
     * The colour after {@code current}.
     *
     * <p>A colour that is not in the list — every value a hand-edited config can
     * hold — moves to the first preset instead of failing, so a click always
     * does something visible.</p>
     *
     * @return the next preset, wrapping at the end
     */
    public static int next(int current) {
        int index = indexOf(current);
        if (index < 0) {
            return PRESETS.get(0);
        }
        return PRESETS.get((index + 1) % PRESETS.size());
    }
}
