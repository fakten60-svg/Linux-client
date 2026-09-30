package wtf.woke.lite.screen;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.SettingType;

/**
 * The config screen's right pane: the module rows of the selected category and
 * their settings, stacked once and drawn with a single scroll offset.
 *
 * <p>No widget tree is built, so the filter can run on every keystroke. The rows
 * are {@link ConfigRow} data drawn by stateless renderers, the enum list is
 * {@link DropdownOverlay}, and the tooltip lives here because a tooltip belongs
 * to the widget the pointer is over — which here is the pane, not the row.</p>
 */
final class ModuleListWidget extends ClickableWidget {

    private static final int SCROLL_STEP = 12;

    private final ModuleRegistry registry;
    private final Runnable onChanged;
    private final DropdownOverlay overlay = new DropdownOverlay();

    private PaneLayout layout;
    private double scroll;
    private ConfigRows.Placed dragTarget;
    private String tooltipLabel;

    /** @param onChanged run after a switch flips, so the screen re-derives its rows */
    ModuleListWidget(int x, int y, int width, int height, ModuleRegistry registry, Runnable onChanged) {
        super(x, y, width, height, Text.empty());
        this.registry = registry;
        this.onChanged = onChanged;
        this.layout = new PaneLayout(x, y, width, height, List.of());
    }

    /** Replaces the rows shown. Called per keystroke and per selection change. */
    void setRows(List<ConfigRow> rows) {
        layout = new PaneLayout(getX(), getY(), getWidth(), getHeight(), rows);
        scroll = Math.max(0.0, Math.min(scroll, layout.maxScroll()));
        if (overlay.isOpen() && layout.placed().stream().noneMatch(entry -> overlay.isOpenFor(entry.row().key()))) {
            overlay.close();
        }
    }

    /** @return whether an open enum list was closed, so ESC can be offered it first */
    boolean closeOverlay() {
        return overlay.close();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        ConfigTheme.framed(context, getX(), getY(), getWidth(), getHeight(), ConfigTheme.PANEL, ConfigTheme.BORDER);
        ConfigRows.Placed hovered = layout.entryAt(mouseX, mouseY, scroll);

        context.enableScissor(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1);
        if (layout.isEmpty()) {
            context.drawTextWithShadow(renderer, Text.translatable(WokeConfigScreen.EMPTY_KEY),
                    layout.rowX() + 8, layout.y() + 8, ConfigTheme.TEXT_MUTED);
        }
        for (ConfigRows.Placed entry : layout.placed()) {
            int y = layout.rowY(entry, scroll);
            if (y + entry.height() < getY() || y > getBottom()) {
                continue;
            }
            if (entry.row() instanceof ConfigRow.ModuleRow module) {
                ModuleRowWidget.render(context, renderer, module, registry.isRegistered(module.id()), layout.rowX(), y,
                        layout.rowWidth(), entry == hovered);
            } else if (entry.row() instanceof ConfigRow.SettingRow setting) {
                SettingRowWidget.render(context, renderer, setting, layout.rowX(), y, layout.rowWidth(),
                        mouseX, mouseY);
            }
        }
        context.disableScissor();

        renderScrollbar(context);
        if (overlay.isOpen()) {
            overlay.render(context, renderer, layout, scroll, mouseX, mouseY);
        }
        updateTooltip(hovered, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!isInteractable() || click.button() != 0) {
            return false;
        }
        if (overlay.isOpen()) {
            return overlay.click(layout, scroll, click.x(), click.y());
        }
        ConfigRows.Placed entry = layout.entryAt(click.x(), click.y(), scroll);
        if (entry == null) {
            return false;
        }
        if (activate(entry, click.x(), click.y())) {
            playDownSound(MinecraftClient.getInstance().getSoundManager());
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragTarget == null) {
            return false;
        }
        SettingValueWidget.pick(((ConfigRow.SettingRow) dragTarget.row()).setting(), click.x(), layout.rowX(),
                layout.rowWidth());
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragTarget = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double max = layout.maxScroll();
        if (max <= 0.0) {
            return false;
        }
        scroll = Math.max(0.0, Math.min(max, scroll - vertical * SCROLL_STEP));
        return true;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    /** @return whether the click did something, so the click sound is earned */
    private boolean activate(ConfigRows.Placed entry, double mouseX, double mouseY) {
        int x = layout.rowX();
        int y = layout.rowY(entry, scroll);
        int width = layout.rowWidth();
        if (entry.row() instanceof ConfigRow.ModuleRow module) {
            if (!registry.isRegistered(module.id()) || !ModuleRowWidget.switchHit(mouseX, mouseY, x, y, width)) {
                return false;
            }
            registry.setEnabled(module.id(), !module.enabled());
            onChanged.run();
            return true;
        }
        ConfigRow.SettingRow row = (ConfigRow.SettingRow) entry.row();
        if (SettingRowWidget.resetHit(mouseX, mouseY, x, y, width)) {
            row.setting().reset();
            return true;
        }
        if (!SettingRowWidget.valueHit(mouseX, mouseY, x, y, width)) {
            return false;
        }
        Setting<?> setting = row.setting();
        if (setting.type() == SettingType.ENUM) {
            overlay.open(entry);
            return true;
        }
        dragTarget = setting.type() == SettingType.INT || setting.type() == SettingType.DOUBLE ? entry : null;
        SettingValueWidget.pick(setting, mouseX, x, width);
        return true;
    }

    private void updateTooltip(        ConfigRows.Placed hovered, double mouseX, double mouseY) {
        Text tooltip = hovered != null && hovered.row() instanceof ConfigRow.SettingRow row
                ? SettingRowWidget.tooltipAt(row, mouseX, mouseY, layout.rowX(), layout.rowY(hovered, scroll),
                        layout.rowWidth())
                : null;
        String label = tooltip == null ? null : tooltip.getString();
        if (Objects.equals(label, tooltipLabel)) {
            return;
        }
        tooltipLabel = label;
        setTooltip(tooltip == null ? null : Tooltip.of(tooltip));
    }

    private void renderScrollbar(DrawContext context) {
        double max = layout.maxScroll();
        if (max <= 0.0) {
            return;
        }
        int barHeight = Math.max(12, (int) (getHeight() * getHeight() / (double) layout.contentHeight()));
        int barY = getY() + (int) ((getHeight() - barHeight) * (scroll / max));
        ConfigTheme.fill(context, getX() + getWidth() - 4, barY, 3, barHeight, ConfigTheme.BORDER);
    }

}
