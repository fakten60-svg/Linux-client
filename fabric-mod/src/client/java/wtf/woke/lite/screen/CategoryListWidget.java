package wtf.woke.lite.screen;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import wtf.woke.lite.core.ModuleCategory;

/**
 * The config screen's left pane: one row per category, each showing how many
 * modules it holds.
 *
 * <p>The badge counts what survived the query against everything the category
 * has, so a filtered list still says what it is hiding — otherwise a search that
 * matched one module in a category of six looks like that category always had
 * one.</p>
 *
 * <p>Selecting a category is not the same as folding it: clicking the category
 * that is already open folds it, which is what makes the sidebar collapsible
 * without a second control to explain.</p>
 */
final class CategoryListWidget extends ClickableWidget {

    private static final int TOP_PADDING = 4;
    private static final int SIDE_PADDING = 3;
    private static final int ROW_GAP = 2;

    private final Consumer<ModuleCategory> onSelect;
    private List<ConfigRow.CategoryRow> rows = List.of();
    private ModuleCategory selected = ModuleCategory.HUD;

    CategoryListWidget(int x, int y, int width, int height, Consumer<ModuleCategory> onSelect) {
        super(x, y, width, height, Text.empty());
        this.onSelect = onSelect;
    }

    /** Replaces the categories shown, in the order the list should stack them. */
    void setRows(List<ConfigRow.CategoryRow> newRows) {
        this.rows = List.copyOf(newRows);
    }

    void setSelected(ModuleCategory category) {
        this.selected = category;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        ConfigTheme.framed(context, getX(), getY(), getWidth(), getHeight(), ConfigTheme.PANEL, ConfigTheme.BORDER);
        int y = getY() + TOP_PADDING;
        for (ConfigRow.CategoryRow row : rows) {
            int height = ConfigRow.CATEGORY_HEIGHT - ROW_GAP;
            boolean open = row.category() == selected;
            boolean hovered = inside(mouseX, mouseY, y, height);
            ConfigTheme.fill(context, getX() + SIDE_PADDING, y, getWidth() - 2 * SIDE_PADDING, height,
                    open ? ConfigTheme.ROW_HOVER : hovered ? ConfigTheme.BAR_HOVER : ConfigTheme.BAR);
            if (open) {
                ConfigTheme.marker(context, getX() + SIDE_PADDING, y, height, ConfigTheme.ACCENT);
            }
            int textY = y + (height - 8) / 2;
            context.drawTextWithShadow(renderer, Text.translatable(row.category().translationKey()),
                    getX() + SIDE_PADDING + 10, textY, open ? ConfigTheme.ACCENT : ConfigTheme.TEXT);
            Text badge = Text.literal(badge(row));
            context.drawTextWithShadow(renderer, badge,
                    getX() + getWidth() - SIDE_PADDING - 6 - renderer.getWidth(badge), textY, ConfigTheme.TEXT_DIM);
            y += ConfigRow.CATEGORY_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!isInteractable() || click.button() != 0) {
            return false;
        }
        int index = (int) ((click.y() - (getY() + TOP_PADDING)) / ConfigRow.CATEGORY_HEIGHT);
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        onSelect.accept(rows.get(index).category());
        playDownSound(MinecraftClient.getInstance().getSoundManager());
        return true;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    private boolean inside(double mouseX, double mouseY, int y, int height) {
        return mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= y && mouseY < y + height;
    }

    private static String badge(ConfigRow.CategoryRow row) {
        return row.isFiltered() ? row.shown() + "/" + row.total() : Integer.toString(row.total());
    }
}
