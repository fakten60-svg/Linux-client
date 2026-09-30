package wtf.woke.lite.screen;

import java.util.function.Consumer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * The search box at the top of the config screen.
 *
 * <p>Only a {@link TextFieldWidget} with the screen's colours baked in. The
 * widget keeps the editing behaviour — caret, selection, clipboard, arrow keys —
 * and stops painting its own vanilla box, because the screen draws one in
 * {@link ConfigTheme} instead. Rebuilding that behaviour by hand is exactly the
 * kind of work the vanilla widget exists to prevent.</p>
 *
 * <p>The callback fires on every keystroke, which is what makes the filter live:
 * the screen re-derives its rows from the query rather than filtering a list of
 * widgets, so there is no list to rebuild while the player types.</p>
 */
public final class SearchFieldWidget extends TextFieldWidget {

    /**
     * @param textRenderer the renderer the field measures and draws with
     * @param x            left edge
     * @param y            top edge
     * @param width        width in GUI units
     * @param height       height in GUI units
     * @param placeholder  shown while the field is empty
     * @param onChange     called with the new text after every change
     */
    public SearchFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text placeholder,
            Consumer<String> onChange) {
        super(textRenderer, x, y, width, height, Text.empty());
        setPlaceholder(placeholder);
        setMaxLength(64);
        setDrawsBackground(false);
        setEditableColor(ConfigTheme.TEXT);
        setUneditableColor(ConfigTheme.TEXT_MUTED);
        setChangedListener(onChange);
        setTooltip(Tooltip.of(Text.translatable(WokeConfigScreen.SEARCH_TOOLTIP_KEY)));
    }
}
