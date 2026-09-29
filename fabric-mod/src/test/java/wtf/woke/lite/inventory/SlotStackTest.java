package wtf.woke.lite.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlotStackTest {

    @Test
    void contentIsEitherEmptyOrACountableStack() {
        assertEquals(StackKey.EMPTY, SlotStack.EMPTY.key());
        assertEquals(new StackKey("Stone", 12), new SlotStack("Stone", 12, 64).key());

        assertThrows(IllegalArgumentException.class, () -> new SlotStack("Stone", 65, 64), "more than fits");
        assertThrows(IllegalArgumentException.class, () -> new SlotStack("Stone", 1, 0), "no stack size");
        assertThrows(IllegalArgumentException.class, () -> new SlotStack("Stone", -1, 64));
        assertThrows(IllegalArgumentException.class, () -> new SlotStack("", 4, 64), "items need a name");
        assertThrows(IllegalArgumentException.class, () -> new SlotStack("Stone", 0, 64), "an empty slot is unnamed");
        assertThrows(IllegalArgumentException.class, () -> SlotStack.EMPTY.withCount(2), "a stack needs a name");
    }

    @Test
    void onlySameNamedStackableItemsWouldBeCombined() {
        SlotStack stone5 = new SlotStack("Stone", 5, 64);
        SlotStack stone64 = new SlotStack("Stone", 64, 64);
        SlotStack apple = new SlotStack("Apple", 5, 64);
        SlotStack pickaxe = new SlotStack("Pickaxe", 1, 1);

        assertTrue(stone5.mergesWith(stone64));
        assertFalse(stone5.mergesWith(apple), "different items are swapped, not combined");
        assertFalse(pickaxe.mergesWith(new SlotStack("Pickaxe", 1, 1)), "unstackable items are swapped");
        assertFalse(stone5.mergesWith(SlotStack.EMPTY), "nothing to combine");
    }

    @Test
    void countsAndFreeSpaceAddUpToTheStackLimit() {
        SlotStack stone = new SlotStack("Stone", 5, 64);

        assertEquals(59, stone.freeSpace());
        assertEquals(64, stone.withCount(64).count());
        assertEquals(0, stone.withCount(64).freeSpace());
        assertEquals(StackKey.EMPTY, stone.withCount(0).key(), "an emptied slot is the empty slot");
        assertEquals(SlotStack.EMPTY, stone.withCount(0));
    }

    @Test
    void changingTheCountLeavesTheOriginalAlone() {
        SlotStack stone = new SlotStack("Stone", 5, 64);

        assertEquals(stone, stone.withCount(5));
        assertEquals(5, stone.count());
        assertEquals(64, stone.maxCount());
    }

    @Test
    void theEmptySlotIsUnnamed() {
        assertTrue(SlotStack.EMPTY.isEmpty());
        assertEquals("", SlotStack.EMPTY.name());
        assertEquals(0, SlotStack.EMPTY.count());
    }
}
