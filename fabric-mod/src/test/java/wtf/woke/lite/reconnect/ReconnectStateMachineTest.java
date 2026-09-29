package wtf.woke.lite.reconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import wtf.woke.lite.reconnect.ReconnectStateMachine.Step;

/**
 * Pins the three promises {@link ReconnectStateMachine} makes: the countdown
 * fires a connect exactly once, a second start cannot be queued behind the
 * first, and a cancelled or timed-out sequence can never connect.
 */
class ReconnectStateMachineTest {

    private static final int COUNTDOWN = 40;
    private static final int TIMEOUT = 100;

    private final ReconnectStateMachine machine = new ReconnectStateMachine();

    @Test
    void startsIdleAndDoesNothing() {
        assertTrue(machine.isIdle());
        assertEquals(ReconnectState.IDLE, machine.state());
        assertEquals(0, machine.remainingTicks());
        assertEquals(Step.WAITING, machine.tick());
    }

    @Test
    void countsDownInTicksAndConnectsExactlyOnce() {
        assertTrue(machine.start(COUNTDOWN, TIMEOUT));
        assertTrue(machine.isCounting());

        for (int tick = 1; tick < COUNTDOWN; tick++) {
            assertEquals(Step.COUNTING, machine.tick(), "tick " + tick + " must not connect yet");
            assertTrue(machine.isCounting());
        }

        assertEquals(Step.CONNECT, machine.tick());
        assertTrue(machine.isConnecting());
        assertFalse(machine.isCounting());

        // Every later tick is a no-op: one attempt, one connection.
        for (int tick = 0; tick < 10; tick++) {
            assertEquals(Step.WAITING, machine.tick());
        }
        assertTrue(machine.isConnecting());
    }

    @Test
    void aSecondStartIsRefusedWhileCounting() {
        assertTrue(machine.start(COUNTDOWN, TIMEOUT));
        assertFalse(machine.start(COUNTDOWN, TIMEOUT));
        assertEquals(COUNTDOWN, machine.remainingTicks(), "the refused start must not reset the countdown");
    }

    @Test
    void aSecondStartIsRefusedWhileConnecting() {
        assertTrue(machine.start(1, TIMEOUT));
        assertEquals(Step.CONNECT, machine.tick());

        assertFalse(machine.start(COUNTDOWN, TIMEOUT));
        assertTrue(machine.isConnecting(), "the refused start must not arm another attempt");
    }

    @Test
    void cancelStopsTheCountdown() {
        machine.start(COUNTDOWN, TIMEOUT);
        machine.tick();

        assertTrue(machine.cancel());
        assertEquals(ReconnectState.CANCELLED, machine.state());
        assertEquals(0, machine.remainingTicks());

        // No tick after a cancellation may ever connect.
        for (int tick = 0; tick < COUNTDOWN * 2; tick++) {
            assertEquals(Step.WAITING, machine.tick());
        }
    }

    @Test
    void cancelIsRefusedWhenThereIsNothingToCancel() {
        assertFalse(machine.cancel());
        machine.start(COUNTDOWN, TIMEOUT);
        machine.markConnected();
        assertFalse(machine.cancel());
    }

    @Test
    void cancelWhileConnectingEndsTheSequence() {
        machine.start(1, TIMEOUT);
        assertEquals(Step.CONNECT, machine.tick());

        assertTrue(machine.cancel());
        assertEquals(ReconnectState.CANCELLED, machine.state());
        assertEquals(Step.WAITING, machine.tick());
    }

    @Test
    void aCancelledSequenceCanBeStartedAgain() {
        machine.start(COUNTDOWN, TIMEOUT);
        machine.cancel();

        assertTrue(machine.start(COUNTDOWN, TIMEOUT));
        assertTrue(machine.isCounting());
    }

    @Test
    void anAttemptThatNeverJoinsTimesOut() {
        machine.start(1, 3);
        assertEquals(Step.CONNECT, machine.tick());

        assertEquals(Step.WAITING, machine.tick());
        assertEquals(Step.WAITING, machine.tick());
        assertEquals(Step.TIMED_OUT, machine.tick());
        assertTrue(machine.isIdle(), "a timed-out attempt must not leave the machine connecting");
    }

    @Test
    void markConnectedEndsTheAttempt() {
        machine.start(1, TIMEOUT);
        machine.tick();

        assertTrue(machine.markConnected());
        assertTrue(machine.isIdle());
        assertFalse(machine.markConnected(), "there was nothing left to end");
    }

    @Test
    void remainingSecondsRoundsUp() {
        // A fresh machine per case: reusing one would be refused by the very
        // rule the neighbouring test pins, and would test nothing.
        assertEquals(1, secondsForCountdown(20));
        assertEquals(2, secondsForCountdown(21));
        assertEquals(3, secondsForCountdown(60));
        assertEquals(0, secondsForCountdown(0));
    }

    private static int secondsForCountdown(int countdownTicks) {
        ReconnectStateMachine fresh = new ReconnectStateMachine();
        if (countdownTicks > 0) {
            fresh.start(countdownTicks, TIMEOUT);
        }
        return fresh.remainingSeconds();
    }

    @Test
    void rejectsNonPositiveDurations() {
        assertThrows(IllegalArgumentException.class, () -> machine.start(0, TIMEOUT));
        assertThrows(IllegalArgumentException.class, () -> machine.start(COUNTDOWN, 0));
    }
}
