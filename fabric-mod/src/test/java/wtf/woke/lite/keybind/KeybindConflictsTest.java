package wtf.woke.lite.keybind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pins what counts as a collision, and what must not. */
class KeybindConflictsTest {

    private static Map<String, String> bindings(String... ownerKeyPairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < ownerKeyPairs.length; index += 2) {
            map.put(ownerKeyPairs[index], ownerKeyPairs[index + 1]);
        }
        return map;
    }

    @Test
    void uniqueKeysNeverConflict() {
        List<KeybindConflicts.Conflict> conflicts = KeybindConflicts.find(
                bindings("ours", "key.keyboard.g", "theirs", "key.keyboard.h"));

        assertEquals(List.of(), conflicts);
    }

    @Test
    void aSharedKeyIsReportedWithBothOwners() {
        List<KeybindConflicts.Conflict> conflicts = KeybindConflicts.find(
                bindings("ours", "key.keyboard.g", "theirs", "key.keyboard.g"));

        assertEquals(1, conflicts.size());
        assertEquals("key.keyboard.g", conflicts.get(0).key());
        assertEquals(List.of("ours", "theirs"), conflicts.get(0).owners());
        assertEquals(2, conflicts.get(0).ownerCount());
    }

    @Test
    void unboundIsNeverAConflict() {
        List<KeybindConflicts.Conflict> conflicts = KeybindConflicts.find(
                bindings("one", KeybindConflicts.UNBOUND, "two", KeybindConflicts.UNBOUND));

        assertEquals(List.of(), conflicts);
    }

    @Test
    void unboundDoesNotHideARealConflictElsewhere() {
        List<KeybindConflicts.Conflict> conflicts = KeybindConflicts.find(bindings(
                "unbound", KeybindConflicts.UNBOUND,
                "ours", "key.mouse.left",
                "theirs", "key.mouse.left"));

        assertEquals(1, conflicts.size());
        assertEquals("key.mouse.left", conflicts.get(0).key());
    }

    @Test
    void everyOwnerIsListedInTheOrderItWasSupplied() {
        List<KeybindConflicts.Conflict> conflicts = KeybindConflicts.find(bindings(
                "a", "key.keyboard.k", "b", "key.keyboard.k", "c", "key.keyboard.k"));

        assertEquals(1, conflicts.size());
        assertEquals(List.of("a", "b", "c"), conflicts.get(0).owners());
    }

    @Test
    void malformedEntriesAreIgnored() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("nullKey", null);
        map.put("blankKey", "  ");
        map.put(null, "key.keyboard.g");
        map.put("fine", "key.keyboard.g");

        assertEquals(List.of(), KeybindConflicts.find(map));
    }

    @Test
    void anEmptyMapHasNoConflicts() {
        assertTrue(KeybindConflicts.find(Map.of()).isEmpty());
    }
}
