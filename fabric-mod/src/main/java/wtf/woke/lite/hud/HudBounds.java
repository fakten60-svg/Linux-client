package wtf.woke.lite.hud;

/**
 * An immutable integer rectangle in GUI units.
 *
 * <p>Used for both a placed element's on-screen box and an element's local
 * drawing box, so placement code and drawing code speak the same language.
 * No Minecraft types, so it is unit-testable without a game.</p>
 *
 * @param x      left edge
 * @param y      top edge
 * @param width  width, never negative
 * @param height height, never negative
 */
public record HudBounds(int x, int y, int width, int height) {

    public HudBounds {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width and height must be >= 0, got " + width + "x" + height);
        }
    }

    public static HudBounds at(int x, int y, int width, int height) {
        return new HudBounds(x, y, width, height);
    }

    /** @return a rectangle at the origin with the given size */
    public static HudBounds sized(int width, int height) {
        return new HudBounds(0, 0, width, height);
    }

    /** @return the first column past the right edge */
    public int right() {
        return x + width;
    }

    /** @return the first row past the bottom edge */
    public int bottom() {
        return y + height;
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }

    /** @return the same size, moved by the given deltas */
    public HudBounds offset(int deltaX, int deltaY) {
        return new HudBounds(x + deltaX, y + deltaY, width, height);
    }

    /** @return the same size, grown by {@code amount} on every side */
    public HudBounds expanded(int amount) {
        return new HudBounds(x - amount, y - amount, width + 2 * amount, height + 2 * amount);
    }
}
