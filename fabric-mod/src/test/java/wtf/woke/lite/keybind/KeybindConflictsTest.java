package wtf.woke.lite.keybind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // --- involving: only conflicts the caller's bindings take part in ---------

    @Test
    void anOwnBindingClashingWithVanillaIsReported() {
        List<KeybindConflicts.Conflict> conflicts = KeybindConflicts.involving(
                bindings("config.open", "key.keyboard.f1", "key.debug.help", "key.keyboard.f1"),
                Set.of("config.open"));

        assertEquals(1, conflicts.size());
        assertEquals("key.keyboard.f1", conflicts.get(0).key());
        assertEquals(List.of("config.open", "key.debug.help"), conflicts.get(0).owners());
    }

    @Test
    void twoVanillaBindingsSharingAKeyAreNotReported() {
        List<KeybindConflicts.Conflict> conflicts = KeybindConflicts.involving(
                bindings("key.left", "key.keyboard.a", "key.debug.reloadChunk", "key.keyboard.a"),
                Set.of("config.open"));

        assertEquals(List.of(), conflicts);
    }

    @Test
    void anOwnBindingClashingWithAnotherOwnBindingIsReported() {
        List<KeybindConflicts.Conflict> conflicts = KeybindConflicts.involving(
                bindings("config.open", "key.keyboard.g", "util.fullscreen_key.keybind", "key.keyboard.g"),
                Set.of("config.open", "util.fullscreen_key.keybind"));

        assertEquals(1, conflicts.size());
        assertEquals(List.of("config.open", "util.fullscreen_key.keybind"), conflicts.get(0).owners());
    }

    @Test
    void involvingKeepsOnlyTheOwnPairsFromAMixedMap() {
        List<KeybindConflicts.Conflict> conflicts = KeybindConflicts.involving(bindings(
                "key.left", "key.keyboard.a",
                "key.debug.reloadChunk", "key.keyboard.a",
                "config.open", "key.keyboard.f1",
                "key.debug.help", "key.keyboard.f1"),
                Set.of("config.open"));

        assertEquals(1, conflicts.size());
        assertEquals("key.keyboard.f1", conflicts.get(0).key());
    }

    @Test
    void involvingWithoutAnyOwnBindingsReportsNothing() {
        List<KeybindConflicts.Conflict> conflicts = KeybindConflicts.involving(
                bindings("key.left", "key.keyboard.a", "key.debug.reloadChunk", "key.keyboard.a"),
                Set.of());

        assertEquals(List.of(), conflicts);
    }
}
