package wtf.woke.lite.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StackKeyTest {

    @Test
    void anEmptySlotHasNoNameAndNoCount() {
        assertTrue(StackKey.EMPTY.isEmpty());
        assertEquals("", StackKey.EMPTY.name());
        assertEquals(0, StackKey.EMPTY.count());
    }

    @Test
    void aSlotIsEitherEmptyOrHoldsAtLeastOneNamedItem() {
        assertFalse(new StackKey("Stone", 1).isEmpty());
        assertFalse(new StackKey("Stone", 64).isEmpty());

        assertThrows(IllegalArgumentException.class, () -> new StackKey("", 3), "items need a name");
        assertThrows(IllegalArgumentException.class, () -> new StackKey("Stone", 0), "an empty stack has no name");
        assertThrows(IllegalArgumentException.class, () -> new StackKey("Stone", -1));
        assertThrows(NullPointerException.class, () -> new StackKey(null, 1));
    }

    @Test
    void orderingIsByNameThenCount() {
        assertTrue(new StackKey("Apple", 64).compareTo(new StackKey("Stone", 1)) < 0);
        assertEquals(0, new StackKey("Stone", 5).compareTo(new StackKey("Stone", 5)));
        assertTrue(new StackKey("Stone", 5).compareTo(new StackKey("Stone", 64)) < 0);
        assertTrue(new StackKey("Stone", 64).compareTo(new StackKey("Stone", 5)) > 0);
    }
}
