package wtf.woke.lite.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SortResultTest {

    @Test
    void onlyTheOutcomesThatAreNotComplaintsCountAsSuccess() {
        assertTrue(SortResult.STARTED.isSuccess());
        assertTrue(SortResult.ALREADY_SORTED.isSuccess(), "nothing to do is not a failure");

        assertFalse(SortResult.BUSY.isSuccess());
        assertFalse(SortResult.DISABLED.isSuccess());
        assertFalse(SortResult.NOT_READY.isSuccess());
        assertFalse(SortResult.UNAVAILABLE.isSuccess());
    }

    @Test
    void everyConstantHasItsOwnTranslationKey() {
        long distinctKeys = Arrays.stream(SortResult.values()).map(SortResult::translationKey).distinct().count();

        assertEquals(SortResult.values().length, distinctKeys);
        assertEquals("wokewtf.lite.sort_result.started", SortResult.STARTED.translationKey());
        assertEquals("wokewtf.lite.sort_result.unavailable", SortResult.UNAVAILABLE.translationKey());
    }
}
