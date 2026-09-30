package wtf.woke.lite.screen;

/**
 * Turns a pixel on a slider track into a value and back.
 *
 * <p>Outside the client because this is the part of a slider that can be wrong
 * without looking wrong: an off-by-one range, a reversed axis, a division by a
 * zero-width track. Nothing here needs a screen to be checked, so the three
 * directions — where the knob goes, what a click stores, and what a drag
 * stores — are pinned by tests instead of by dragging a mouse around.</p>
 *
 * <p>Every method clamps. A slider whose track is partly scrolled out of view,
 * or a click that lands a pixel past the end, must land on the nearest allowed
 * value rather than outside the range the validator keeps.</p>
 */
public final class SliderPosition {

    private SliderPosition() {
        throw new AssertionError("No instances of " + SliderPosition.class.getName());
    }

    /**
     * @param min   lowest value of the range
     * @param max   highest value of the range
     * @param value the value to place
     * @return where on the track the knob belongs, {@code 0} at the left end and
     *         {@code 1} at the right; {@code 0} when the range is empty
     */
    public static double fractionOf(double min, double max, double value) {
        if (!(max > min)) {
            return 0.0;
        }
        return clamp((value - min) / (max - min));
    }

    /**
     * @param min      lowest value of the range
     * @param max      highest value of the range
     * @param fraction position along the track, {@code 0..1}
     * @return the value at that position, clamped to the range
     */
    public static double valueOf(double min, double max, double fraction) {
        return min + (max - min) * clamp(fraction);
    }

    /**
     * @param trackX     left edge of the track, in GUI units
     * @param trackWidth width of the track, in GUI units
     * @param mouseX     pointer position in the same units
     * @return the fraction of the track the pointer sits at, clamped to
     *         {@code 0..1}; {@code 0} for a track with no width
     */
    public static double fractionAt(double trackX, double trackWidth, double mouseX) {
        if (!(trackWidth > 0.0)) {
            return 0.0;
        }
        return clamp((mouseX - trackX) / trackWidth);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
