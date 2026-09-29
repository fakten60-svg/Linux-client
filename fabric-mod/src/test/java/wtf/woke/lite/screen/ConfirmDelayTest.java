package wtf.woke.lite.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the one property that matters for a gate like this: it fires once, on
 * the tick it runs out, and never again until it is armed afresh.
 */
class ConfirmDelayTest {

    private final ConfirmDelay delay = new ConfirmDelay();

    @Test
    void aFreshGateHoldsNothingBack() {
        assertFalse(delay.isHolding());
        assertFalse(delay.tick());
        assertEquals(0, delay.remainingSeconds());
    }

    @Test
    void armsForWholeSeconds() {
        delay.arm(3);

        assertTrue(delay.isHolding());
        assertEquals(3, delay.remainingSeconds());
        assertEquals(3 * ConfirmDelay.TICKS_PER_SECOND, ticksUntilRelease());
    }

    @Test
    void firesExactlyOnce() {
        delay.arm(1);

        for (int tick = 1; tick < ConfirmDelay.TICKS_PER_SECOND; tick++) {
            assertFalse(delay.tick(), "tick " + tick + " must not release yet");
        }
        assertTrue(delay.tick(), "the last tick releases");

        assertFalse(delay.isHolding());
        for (int tick = 0; tick < 5; tick++) {
            assertFalse(delay.tick(), "a released gate stays released");
        }
    }

    @Test
    void remainingSecondsRoundsUp() {
        delay.arm(2);
        delay.tick();

        assertEquals(2, delay.remainingSeconds());
        for (int tick = 0; tick < ConfirmDelay.TICKS_PER_SECOND; tick++) {
            delay.tick();
        }
        assertEquals(1, delay.remainingSeconds());
    }

    @Test
    void clearDisarmsImmediately() {
        delay.arm(5);
        delay.clear();

        assertFalse(delay.isHolding());
        assertEquals(0, delay.remainingSeconds());
        assertFalse(delay.tick());
    }

    @Test
    void armingBelowOneSecondStillHoldsForOne() {
        delay.arm(0);

        assertTrue(delay.isHolding());
        assertEquals(1, delay.remainingSeconds());

        delay.arm(-4);
        assertEquals(1, delay.remainingSeconds());
    }

    @Test
    void rearmingRestartsTheHold() {
        delay.arm(1);
        delay.tick();
        delay.tick();

        delay.arm(1);

        assertEquals(ConfirmDelay.TICKS_PER_SECOND, ticksUntilRelease());
    }

    /**
     * @return how many ticks it takes for the armed gate to release, including
     *         the releasing one; only valid while the gate is holding
     */
    private int ticksUntilRelease() {
        int ticks = 0;
        while (!delay.tick()) {
            ticks++;
        }
        return ticks + 1;
    }
}
