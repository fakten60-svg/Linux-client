package wtf.woke.lite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DebugWatchTest {

    @Test
    void nothingIsWatchedByDefault() {
        DebugWatch watch = new DebugWatch();

        assertEquals("", watch.target());
        assertFalse(watch.watches("hud.fps"));
        assertFalse(watch.tickLineDue(), "no target means no tick lines at all");
    }

    @Test
    void watchTrimsAndKeepsTheId() {
        DebugWatch watch = new DebugWatch();
        watch.watch("  ui.chat  ");

        assertEquals("ui.chat", watch.target());
        assertTrue(watch.watches("ui.chat"));
        assertFalse(watch.watches("ui.cha"), "a prefix is not the module");
        assertFalse(watch.watches("ui.chats"), "neither is an extension of it");
    }

    @Test
    void blankAndNullStopTheWatch() {
        DebugWatch watch = new DebugWatch();
        watch.watch("hud.fps");
        watch.watch("   ");

        assertEquals("", watch.target());
        assertFalse(watch.watches("hud.fps"));
        assertFalse(watch.tickLineDue());

        watch.watch("hud.fps");
        watch.watch(null);
        assertEquals("", watch.target(), "a typo must switch logging off, not widen it");
        assertFalse(watch.tickLineDue());
    }

    @Test
    void theFirstTickIsAlwaysLoggedThenEveryHundredth() {
        DebugWatch watch = new DebugWatch();
        watch.watch("hud.fps");

        assertTrue(watch.tickLineDue(), "turning the watch on answers straight away");
        for (int i = 2; i < DebugWatch.TICK_LOG_INTERVAL; i++) {
            assertFalse(watch.tickLineDue(), "tick " + i + " should stay quiet");
        }
        assertTrue(watch.tickLineDue(), "the hundredth tick is due");
    }

    @Test
    void switchingTargetRestartsTheCount() {
        DebugWatch watch = new DebugWatch();
        watch.watch("hud.fps");
        assertTrue(watch.tickLineDue());

        watch.watch("ui.chat");
        assertEquals("ui.chat", watch.target());
        assertTrue(watch.tickLineDue(), "a new target answers straight away again");
    }

    @Test
    void reWatchingTheSameIdDoesNotRestartTheCount() {
        DebugWatch watch = new DebugWatch();
        watch.watch("hud.fps");
        assertTrue(watch.tickLineDue());

        watch.watch("hud.fps");
        assertFalse(watch.tickLineDue(), "an unchanged target keeps counting");
    }
}
