package wtf.woke.lite.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;

/**
 * Pins the macro editing rules and, more importantly, the encoded form: it is
 * what the config stores, so a change in how it is split silently reinterprets
 * every existing entry.
 */
class ChatMacroListTest {

    private static Setting<List<String>> emptySetting() {
        return Settings.stringList("macros", List.of(), ChatMacroList.MAX_ENTRIES, ChatMacroList.MAX_ENCODED_LENGTH);
    }

    @Test
    void encodesAndDecodesAgain() {
        ChatMacro macro = new ChatMacro("greet", "Hello there");

        assertEquals("greet|Hello there", macro.encode());
        assertEquals(Optional.of(macro), ChatMacro.decode(macro.encode()));
    }

    @Test
    void theTextMayContainTheSeparator() {
        ChatMacro macro = new ChatMacro("table", "a|b|c");

        assertEquals(Optional.of(macro), ChatMacro.decode(macro.encode()));
        assertEquals("a|b|c", ChatMacro.decode("table|a|b|c").orElseThrow().text());
    }

    @Test
    void unreadableEntriesDecodeToEmpty() {
        assertTrue(ChatMacro.decode(null).isEmpty());
        assertTrue(ChatMacro.decode("no separator here").isEmpty());
        assertTrue(ChatMacro.decode("|no name").isEmpty());
        assertTrue(ChatMacro.decode("name|").isEmpty());
    }

    @Test
    void nameRulesAreEnforced() {
        assertTrue(ChatMacro.isValidName("home"));
        assertFalse(ChatMacro.isValidName(null));
        assertFalse(ChatMacro.isValidName("   "));
        assertFalse(ChatMacro.isValidName("with|pipe"));
        assertFalse(ChatMacro.isValidName("x".repeat(ChatMacro.MAX_NAME_LENGTH + 1)));
        assertTrue(ChatMacro.isValidName("x".repeat(ChatMacro.MAX_NAME_LENGTH)));

        assertThrows(IllegalArgumentException.class, () -> new ChatMacro("bad|name", "text"));
    }

    @Test
    void textRulesAreEnforced() {
        assertTrue(ChatMacro.isValidText("hello"));
        assertFalse(ChatMacro.isValidText(null));
        assertFalse(ChatMacro.isValidText("  "));
        assertFalse(ChatMacro.isValidText("x".repeat(ChatMacro.MAX_TEXT_LENGTH + 1)));
        assertTrue(ChatMacro.isValidText("x".repeat(ChatMacro.MAX_TEXT_LENGTH)));

        assertThrows(IllegalArgumentException.class, () -> new ChatMacro("name", ""));
    }

    @Test
    void storesAndReplacesByName() {
        Setting<List<String>> entries = emptySetting();

        assertEquals(ChatMacroList.Outcome.STORED, ChatMacroList.store(entries, "greet", "Hello"));
        assertEquals(ChatMacroList.Outcome.STORED, ChatMacroList.store(entries, "bye", "Goodbye"));

        assertEquals(ChatMacroList.Outcome.REPLACED, ChatMacroList.store(entries, "greet", "Hi there"));
        assertEquals(2, ChatMacroList.all(entries).size());
        assertEquals("Hi there", ChatMacroList.all(entries).get(0).text());
    }

    @Test
    void storeRejectsBadInput() {
        Setting<List<String>> entries = emptySetting();

        assertEquals(ChatMacroList.Outcome.INVALID_NAME, ChatMacroList.store(entries, "", "text"));
        assertEquals(ChatMacroList.Outcome.INVALID_NAME, ChatMacroList.store(entries, "a|b", "text"));
        assertEquals(ChatMacroList.Outcome.INVALID_TEXT, ChatMacroList.store(entries, "name", ""));
        assertTrue(ChatMacroList.all(entries).isEmpty());
    }

    @Test
    void theListIsBounded() {
        Setting<List<String>> entries = emptySetting();
        for (int index = 0; index < ChatMacroList.MAX_ENTRIES; index++) {
            assertEquals(ChatMacroList.Outcome.STORED, ChatMacroList.store(entries, "m" + index, "text " + index));
        }

        assertEquals(ChatMacroList.Outcome.FULL, ChatMacroList.store(entries, "one too many", "text"));
        assertEquals(ChatMacroList.MAX_ENTRIES, ChatMacroList.all(entries).size());

        // Replacing one of the stored macros is still allowed when full.
        assertEquals(ChatMacroList.Outcome.REPLACED, ChatMacroList.store(entries, "m0", "changed"));
    }

    @Test
    void removeDropsOnlyTheNamedMacro() {
        Setting<List<String>> entries = emptySetting();
        ChatMacroList.store(entries, "greet", "Hello");
        ChatMacroList.store(entries, "bye", "Goodbye");

        assertEquals(ChatMacroList.Outcome.REMOVED, ChatMacroList.remove(entries, "greet"));
        assertEquals(List.of("bye"), ChatMacroList.all(entries).stream().map(ChatMacro::name).toList());
        assertEquals(ChatMacroList.Outcome.NOT_FOUND, ChatMacroList.remove(entries, "greet"));
    }

    @Test
    void slotNumbersAreOneBased() {
        Setting<List<String>> entries = emptySetting();
        ChatMacroList.store(entries, "first", "one");
        ChatMacroList.store(entries, "second", "two");

        assertEquals("one", ChatMacroList.inSlot(entries, 1).orElseThrow().text());
        assertEquals("two", ChatMacroList.inSlot(entries, 2).orElseThrow().text());
        assertTrue(ChatMacroList.inSlot(entries, 0).isEmpty());
        assertTrue(ChatMacroList.inSlot(entries, 3).isEmpty());
    }

    @Test
    void removingAMacroShiftsTheSlotsUp() {
        Setting<List<String>> entries = emptySetting();
        ChatMacroList.store(entries, "first", "one");
        ChatMacroList.store(entries, "second", "two");

        ChatMacroList.remove(entries, "first");

        assertEquals("two", ChatMacroList.inSlot(entries, 1).orElseThrow().text());
    }

    @Test
    void unreadableEntriesAreSkippedNotFatal() {
        List<ChatMacro> macros = ChatMacroList.decodeAll(List.of("good|text", "rubbish", "", "also|fine"));

        assertEquals(2, macros.size());
        assertEquals(List.of("good", "also"), macros.stream().map(ChatMacro::name).toList());
        assertTrue(ChatMacroList.decodeAll(null).isEmpty());
    }

    @Test
    void storeTrimsTheNameItSaves() {
        Setting<List<String>> entries = emptySetting();

        assertEquals(ChatMacroList.Outcome.STORED, ChatMacroList.store(entries, "  greet  ", "Hello"));
        assertEquals("greet", ChatMacroList.all(entries).get(0).name());
    }
}
