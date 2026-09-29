package wtf.woke.lite.modules.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import wtf.woke.lite.book.BookCounterMode;
import wtf.woke.lite.book.BookPageStats;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;

/**
 * Shows how full the page of an open book is.
 *
 * <p>Display only. It reads the page the writing screen is showing and paints a
 * counter next to it; it never edits the page, the book or the item, and it
 * adds no input handling, so the book the player sends to the server is always
 * the book the vanilla screen would have sent.</p>
 *
 * <p>The arithmetic lives in {@code wtf.woke.lite.book} (no game needed) and the
 * single drawing hook in {@code wtf.woke.lite.mixin.client.BookEditScreenMixin}.</p>
 */
public final class BookModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "ui.book";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.ui.book";

    /** Counter colour a fresh install starts with: the hud's soft off-white. */
    public static final int DEFAULT_COLOR = 0xFFE8EAED;

    /** Colour the counter switches to once the page cannot take more text. */
    public static final int FULL_COLOR = 0xFFFF5555;

    private final Setting<BookCounterMode> counter;
    private final Setting<Integer> color;

    public BookModule() {
        this.counter = Settings.choice("counter", BookCounterMode.USED)
                .describedBy(key("counter"), key("counter") + ".tooltip");
        this.color = Settings.color("color", DEFAULT_COLOR).describedBy(key("color"), null);
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String translationKey() {
        return TRANSLATION_KEY;
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.INTERFACE;
    }

    @Override
    public void onRegister(ModuleRegistry registry) {
        addSetting(counter);
        addSetting(color);
    }

    /**
     * @param pageIndex zero-based index of the page the screen is showing
     * @param pages     the book's pages, as the writing screen holds them
     * @return the counter line, already coloured, or empty when there is no such
     *         page
     */
    public Optional<Text> statsLabel(int pageIndex, List<String> pages) {
        Objects.requireNonNull(pages, "pages");
        return BookPageStats.of(pageIndex, pages, WritableBookContentComponent.MAX_PAGE_LENGTH).map(this::format);
    }

    private Text format(BookPageStats stats) {
        BookCounterMode mode = counter.get();
        MutableText label = switch (mode) {
            case USED -> Text.translatable(mode.labelKey(), stats.characters(), stats.characterLimit());
            case REMAINING -> Text.translatable(mode.labelKey(), stats.remaining());
        };
        int argb = stats.isFull() ? FULL_COLOR : color.get();
        return label.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(argb & 0xFFFFFF)));
    }

    private static String key(String settingId) {
        return TRANSLATION_KEY + ".setting." + settingId;
    }
}
