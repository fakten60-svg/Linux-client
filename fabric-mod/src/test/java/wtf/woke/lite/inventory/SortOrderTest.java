package wtf.woke.lite.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SortOrderTest {

    private static final StackKey STONE_64 = new StackKey("Stone", 64);
    private static final StackKey STONE_5 = new StackKey("Stone", 5);
    private static final StackKey APPLE_1 = new StackKey("Apple", 1);

    @Test
    void nameOrderFollowsTheAlphabet() {
        assertEquals(List.of(APPLE_1, STONE_5, STONE_64),
                List.of(STONE_5, APPLE_1, STONE_64).stream().sorted(SortOrder.NAME.comparator()).toList());
    }

    @Test
    void countOrderPutsTheBiggestStackFirstAndBreaksTiesByName() {
        assertEquals(List.of(STONE_64, STONE_5, APPLE_1),
                List.of(APPLE_1, STONE_5, STONE_64).stream().sorted(SortOrder.COUNT.comparator()).toList());
    }

    @Test
    void everyConstantHasItsOwnTranslationKey() {
        long distinctKeys = Arrays.stream(SortOrder.values()).map(SortOrder::translationKey).distinct().count();

        assertEquals(SortOrder.values().length, distinctKeys);
        assertEquals("wokewtf.lite.sort_order.name", SortOrder.NAME.translationKey());
        assertEquals("wokewtf.lite.sort_order.count", SortOrder.COUNT.translationKey());
        assertTrue(SortOrder.values()[0].translationKey().startsWith("wokewtf.lite.sort_order."));
    }
}
