package wtf.woke.lite.hud;

import java.util.Objects;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Everything a HUD element needs to draw itself for one frame.
 *
 * <p>Built once per frame by {@link HudRenderer} and shared by every element, so
 * the mod-wide styling settings resolve in exactly one place and elements stay
 * unaware of configuration.</p>
 *
 * @param drawContext       the vanilla draw context for this frame
 * @param textRenderer      the client's text renderer
 * @param screenWidth       usable width in GUI units
 * @param screenHeight      usable height in GUI units
 * @param backgroundEnabled whether elements should paint their backing plate
 * @param backgroundColor   backing plate colour, ARGB
 * @param textColor         default text colour, ARGB
 */
public record HudContext(DrawContext drawContext, TextRenderer textRenderer, int screenWidth, int screenHeight,
        boolean backgroundEnabled, int backgroundColor, int textColor) {

    public HudContext {
        Objects.requireNonNull(drawContext, "drawContext");
        Objects.requireNonNull(textRenderer, "textRenderer");
        if (screenWidth < 0 || screenHeight < 0) {
            throw new IllegalArgumentException("screen size must be >= 0, got " + screenWidth + "x" + screenHeight);
        }
    }

    /** @return the width of {@code text} in GUI units */
    public int textWidth(String text) {
        return textRenderer.getWidth(text);
    }

    /** @return one line of text height in GUI units */
    public int lineHeight() {
        return textRenderer.fontHeight;
    }
}
