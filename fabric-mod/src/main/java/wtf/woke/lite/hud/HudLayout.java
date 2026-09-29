package wtf.woke.lite.hud;

import java.util.Objects;

/**
 * A HUD element's placement rule: an anchor plus inward offsets and a margin.
 *
 * <p>Immutable, and cheap enough to rebuild whenever a setting changes, which
 * keeps the per-frame render path free of layout arithmetic beyond one
 * {@link #bounds} call. No Minecraft types, so it is unit-testable without a
 * game.</p>
 */
public final class HudLayout {

    /** Default gap between an anchored element and the screen border. */
    public static final int DEFAULT_MARGIN = 4;

    private final HudAnchor anchor;
    private final int offsetX;
    private final int offsetY;
    private final int margin;

    public HudLayout(HudAnchor anchor, int offsetX, int offsetY) {
        this(anchor, offsetX, offsetY, DEFAULT_MARGIN);
    }

    public HudLayout(HudAnchor anchor, int offsetX, int offsetY, int margin) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.offsetX = requireNonNegative(offsetX, "offsetX");
        this.offsetY = requireNonNegative(offsetY, "offsetY");
        this.margin = requireNonNegative(margin, "margin");
    }

    /** @return the anchor this layout pins the element to */
    public HudAnchor anchor() {
        return anchor;
    }

    public int offsetX() {
        return offsetX;
    }

    public int offsetY() {
        return offsetY;
    }

    public int margin() {
        return margin;
    }

    /** @return the same layout with a different anchor */
    public HudLayout withAnchor(HudAnchor newAnchor) {
        return new HudLayout(newAnchor, offsetX, offsetY, margin);
    }

    /** @return the same layout with different offsets */
    public HudLayout withOffsets(int newOffsetX, int newOffsetY) {
        return new HudLayout(anchor, newOffsetX, newOffsetY, margin);
    }

    /**
     * Places an element of the given size.
     *
     * @param elementWidth  measured element width
     * @param elementHeight measured element height
     * @param screenWidth   usable screen width in GUI units
     * @param screenHeight  usable screen height in GUI units
     * @return the element's absolute box, clamped to stay on screen
     */
    public HudBounds bounds(int elementWidth, int elementHeight, int screenWidth, int screenHeight) {
        int x = anchor.resolveX(screenWidth, elementWidth, offsetX, margin);
        int y = anchor.resolveY(screenHeight, elementHeight, offsetY, margin);
        return HudBounds.at(x, y, elementWidth, elementHeight);
    }

    @Override
    public String toString() {
        return anchor + "+(" + offsetX + "," + offsetY + ") margin " + margin;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, got " + value);
        }
        return value;
    }
}
