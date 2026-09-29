package wtf.woke.lite.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ChatTimestampStyleTest {

    @Test
    void hourAndMinuteAreZeroPaddedOnATwentyFourHourClock() {
        assertEquals("14:32", ChatTimestampStyle.HOUR_MINUTE.format(LocalTime.of(14, 32)));
        assertEquals("09:05", ChatTimestampStyle.HOUR_MINUTE.format(LocalTime.of(9, 5)));
        assertEquals("00:00", ChatTimestampStyle.HOUR_MINUTE.format(LocalTime.MIDNIGHT));
        assertEquals("23:59", ChatTimestampStyle.HOUR_MINUTE.format(LocalTime.of(23, 59)));
    }

    @Test
    void theSecondStyleAddsZeroPaddedSeconds() {
        assertEquals("14:32:07", ChatTimestampStyle.HOUR_MINUTE_SECOND.format(LocalTime.of(14, 32, 7)));
        assertEquals("00:00:00", ChatTimestampStyle.HOUR_MINUTE_SECOND.format(LocalTime.MIDNIGHT));
    }

    @Test
    void prefixWrapsTheTimeInBracketsWithATrailingSpace() {
        assertEquals("[14:32] ", ChatTimestampStyle.HOUR_MINUTE.prefix(LocalTime.of(14, 32)));
        assertEquals("[14:32:07] ", ChatTimestampStyle.HOUR_MINUTE_SECOND.prefix(LocalTime.of(14, 32, 7)));
        assertTrue(ChatTimestampStyle.HOUR_MINUTE.prefix(LocalTime.NOON).startsWith(ChatTimestampStyle.PREFIX));
        assertTrue(ChatTimestampStyle.HOUR_MINUTE.prefix(LocalTime.NOON).endsWith(ChatTimestampStyle.SUFFIX));
    }

    @Test
    void aNullTimeIsAProgrammingError() {
        assertThrows(NullPointerException.class, () -> ChatTimestampStyle.HOUR_MINUTE.format(null));
        assertThrows(NullPointerException.class, () -> ChatTimestampStyle.HOUR_MINUTE.prefix(null));
    }

    @Test
    void everyConstantHasADistinctLowercaseKey() {
        long distinct = Arrays.stream(ChatTimestampStyle.values())
                .map(ChatTimestampStyle::translationKey)
                .distinct()
                .count();
        assertEquals(ChatTimestampStyle.values().length, distinct);
        assertEquals("wokewtf.lite.chat_timestamp_style.hour_minute",
                ChatTimestampStyle.HOUR_MINUTE.translationKey());
        assertEquals("wokewtf.lite.chat_timestamp_style.hour_minute_second",
                ChatTimestampStyle.HOUR_MINUTE_SECOND.translationKey());
    }
}
