package wtf.woke.lite.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BookCounterModeTest {

    @Test
    void everyConstantHasDistinctLowercaseKeys() {
        long distinctTranslationKeys = Arrays.stream(BookCounterMode.values())
                .map(BookCounterMode::translationKey)
                .distinct()
                .count();
        long distinctLabelKeys = Arrays.stream(BookCounterMode.values())
                .map(BookCounterMode::labelKey)
                .distinct()
                .count();

        assertEquals(BookCounterMode.values().length, distinctTranslationKeys);
        assertEquals(BookCounterMode.values().length, distinctLabelKeys);
        assertEquals("wokewtf.lite.book_counter_mode.used", BookCounterMode.USED.translationKey());
        assertEquals("wokewtf.lite.book_counter_mode.remaining", BookCounterMode.REMAINING.translationKey());
    }

    @Test
    void labelKeysPointAtDistinctOnScreenFormats() {
        assertEquals("wokewtf.lite.module.ui.book.counter.used", BookCounterMode.USED.labelKey());
        assertEquals("wokewtf.lite.module.ui.book.counter.remaining", BookCounterMode.REMAINING.labelKey());
        assertTrue(BookCounterMode.USED.labelKey().startsWith("wokewtf.lite.module.ui.book.counter."),
                "on-screen formats live under the module's key prefix");
    }
}
