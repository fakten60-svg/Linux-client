package wtf.woke.lite.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatHistoryTest {

    @Test
    void theConfiguredLengthIsClampedIntoRange() {
        assertEquals(ChatHistory.MIN_LENGTH, ChatHistory.limitFor(0));
        assertEquals(ChatHistory.MIN_LENGTH, ChatHistory.limitFor(-500));
        assertEquals(150, ChatHistory.limitFor(150));
        assertEquals(ChatHistory.MAX_LENGTH, ChatHistory.limitFor(9999));
    }

    @Test
    void theVanillaLengthIsAlwaysAFloor() {
        assertEquals(ChatHistory.VANILLA_LENGTH, ChatHistory.limitFor(ChatHistory.VANILLA_LENGTH - 1));
        assertEquals(ChatHistory.VANILLA_LENGTH, ChatHistory.limitFor(1, ChatHistory.VANILLA_LENGTH));
        assertEquals(400, ChatHistory.limitFor(400, ChatHistory.VANILLA_LENGTH));
        assertEquals(500, ChatHistory.limitFor(400, 500), "a larger vanilla cap wins, never this feature");
    }

    @Test
    void theDefaultIsInsideTheSupportedRange() {
        assertTrue(ChatHistory.DEFAULT_LENGTH >= ChatHistory.MIN_LENGTH);
        assertTrue(ChatHistory.DEFAULT_LENGTH <= ChatHistory.MAX_LENGTH);
        assertEquals(ChatHistory.DEFAULT_LENGTH, ChatHistory.limitFor(ChatHistory.DEFAULT_LENGTH));
        assertEquals(ChatHistory.VANILLA_LENGTH, ChatHistory.MIN_LENGTH,
                "the floor must stay vanilla's own cap");
    }
}
