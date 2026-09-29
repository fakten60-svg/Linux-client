package wtf.woke.lite.book;

import java.util.Locale;

/**
 * What the book page counter reports.
 *
 * <p>Both readings answer the same question from opposite ends: {@link #USED}
 * mirrors the vanilla page-number style, {@link #REMAINING} counts down to the
 * moment the page stops accepting text.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 */
public enum BookCounterMode {

    /** {@code 384 / 1024 characters} */
    USED("wokewtf.lite.module.ui.book.counter.used"),

    /** {@code 640 characters left} */
    REMAINING("wokewtf.lite.module.ui.book.counter.remaining");

    private final String labelKey;

    BookCounterMode(String labelKey) {
        this.labelKey = labelKey;
    }

    /**
     * @return the translation key of the format string used on screen; the
     *         placeholders are supplied by {@link BookPageStats}
     */
    public String labelKey() {
        return labelKey;
    }

    /** @return the translation key for this constant's name in the config screen */
    public String translationKey() {
        return "wokewtf.lite.book_counter_mode." + name().toLowerCase(Locale.ROOT);
    }
}
