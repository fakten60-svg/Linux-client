package wtf.woke.lite.screen;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import wtf.woke.lite.core.Labelled;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.SettingType;

/**
 * The value control of one config screen row.
 *
 * <p>Split out of {@link SettingRowWidget} so that neither class has to carry
 * both the row's furniture and every kind of value. Which control a setting gets
 * follows its {@link SettingType}: a boolean is a word you click, a number is a
 * slider across the range the setting was built with, an enum is a label whose
 * list the pane opens, a colour steps through {@link ColorPresets}, and anything
 * else is shown read-only rather than guessed at.</p>
 *
 * <p>Drawing and picking live together here for the same reason they do on the
 * row: the two must agree about where the track is, and deriving both from one
 * helper is the cheapest way to keep them agreeing.</p>
 */
final class SettingValueWidget {

    private static final int TRACK_HEIGHT = 4;

    private SettingValueWidget() {
        throw new AssertionError("No instances of " + SettingValueWidget.class.getName());
    }

    /** Draws the value control for {@code setting}, inside the row at {@code x}. */
    static void render(DrawContext context, TextRenderer renderer, Setting<?> setting, int x, int y, int width) {
        int valueX = SettingRowWidget.valueX(x, width);
        int textY = y + 4;
        Object value = setting.get();
        switch (setting.type()) {
            case BOOLEAN -> context.drawTextWithShadow(renderer, Text.translatable(
                    Boolean.TRUE.equals(value) ? "options.on" : "options.off"), valueX, textY,
                    Boolean.TRUE.equals(value) ? ConfigTheme.ACCENT : ConfigTheme.TEXT_MUTED);
            case INT, DOUBLE -> renderSlider(context, renderer, setting, x, y, width);
            case ENUM -> context.drawTextWithShadow(renderer,
                    Text.literal(enumLabel((Enum<?>) value) + " \u25BE"), valueX, textY, ConfigTheme.TEXT);
            case COLOR_ARGB -> renderColour(context, renderer, (Integer) value, valueX, y);
            default -> context.drawTextWithShadow(renderer, Text.literal(fallbackText(setting, value)),
                    valueX, textY, ConfigTheme.TEXT_MUTED);
        }
    }

    /**
     * Stores what the pointer is pointing at.
     *
     * <p>An enum is left to the caller: it needs a list drawn over the other
     * rows, which is the pane's business and not one row's.</p>
     */
    static void pick(Setting<?> setting, double mouseX, int x, int width) {
        Object value = setting.get();
        switch (setting.type()) {
            case BOOLEAN -> store(setting, !Boolean.TRUE.equals(value));
            case INT -> store(setting, (int) Math.round(valueAt(setting, mouseX, x, width)));
            case DOUBLE -> store(setting, Math.round(valueAt(setting, mouseX, x, width) * 100.0) / 100.0);
            case COLOR_ARGB -> store(setting, ColorPresets.next((Integer) value));
            default -> { }
        }
    }

    /** @return the label of an enum constant, falling back to its Java name */
    static String enumLabel(Enum<?> constant) {
        return constant instanceof Labelled labelled
                ? Text.translatable(labelled.translationKey()).getString()
                : constant.name();
    }

    /** @return the value the pointer position stands for, clamped to the range */
    private static double valueAt(Setting<?> setting, double mouseX, int x, int width) {
        double fraction = SliderPosition.fractionAt(
                SettingRowWidget.trackX(x, width), SettingRowWidget.trackWidth(x, width), mouseX);
        return SliderPosition.valueOf(setting.rangeMin(), setting.rangeMax(), fraction);
    }

    private static void renderSlider(DrawContext context, TextRenderer renderer, Setting<?> setting,
            int x, int y, int width) {
        double value = ((Number) setting.get()).doubleValue();
        int trackX = SettingRowWidget.trackX(x, width);
        int trackWidth = SettingRowWidget.trackWidth(x, width);
        int trackY = y + (ConfigRow.SETTING_HEIGHT - TRACK_HEIGHT) / 2;
        int filled = (int) Math.round(trackWidth
                * SliderPosition.fractionOf(setting.rangeMin(), setting.rangeMax(), value));
        ConfigTheme.fill(context, trackX, trackY, trackWidth, TRACK_HEIGHT, ConfigTheme.BUTTON);
        ConfigTheme.fill(context, trackX, trackY, filled, TRACK_HEIGHT, ConfigTheme.ACCENT_MUTED);
        context.fill(trackX + filled - 1, y + 2, trackX + filled + 1, y + ConfigRow.SETTING_HEIGHT - 2,
                ConfigTheme.ACCENT);
        String text = setting.type() == SettingType.INT
                ? Integer.toString((int) Math.round(value))
                : String.format(Locale.ROOT, "%.2f", value);
        int textX = x + width - SettingRowWidget.resetWidth() - 6 - renderer.getWidth(text);
        context.drawTextWithShadow(renderer, Text.literal(text), textX, y + 4, ConfigTheme.TEXT);
    }

    private static void renderColour(DrawContext context, TextRenderer renderer, int colour, int valueX, int y) {
        int size = ConfigRow.SETTING_HEIGHT - 4;
        ConfigTheme.swatch(context, valueX, y + 2, size, colour);
        context.drawTextWithShadow(renderer, Text.literal(String.format(Locale.ROOT, "#%08X", colour)),
                valueX + size + 4, y + 4, ConfigTheme.TEXT_MUTED);
    }

    private static String fallbackText(Setting<?> setting, Object value) {
        return switch (setting.type()) {
            case KEYBIND -> Text.translatable(String.valueOf(value)).getString();
            case STRING_LIST -> value instanceof List<?> list ? Integer.toString(list.size()) : "";
            default -> String.valueOf(value);
        };
    }

    @SuppressWarnings("unchecked")
    private static void store(Setting<?> setting, Object value) {
        ((Setting<Object>) setting).set(value);
    }
}
