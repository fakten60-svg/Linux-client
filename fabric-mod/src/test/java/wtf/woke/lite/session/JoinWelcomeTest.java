package wtf.woke.lite.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The guide is worth exactly one chat line: these cases pin "once, on the tick
 * after the first join" so a later change cannot turn it into a greeting per
 * join or a line per tick.
 */
class JoinWelcomeTest {

    @Test
    void aFreshGatePrintsNothing() {
        JoinWelcome welcome = new JoinWelcome();

        assertFalse(welcome.consume(), "nothing to print before a join");
        assertFalse(welcome.hasShown());
    }

    @Test
    void aJoinPrintsOnTheNextTick() {
        JoinWelcome welcome = new JoinWelcome();

        welcome.arm();

        assertFalse(welcome.hasShown(), "armed, but not printed yet");
        assertTrue(welcome.consume(), "the tick after the join prints it");
        assertTrue(welcome.hasShown());
    }

    @Test
    void itPrintsOnlyOnce() {
        JoinWelcome welcome = new JoinWelcome();
        welcome.arm();
        assertTrue(welcome.consume());

        assertFalse(welcome.consume(), "later ticks stay quiet");
        welcome.arm();
        assertFalse(welcome.consume(), "a second join does not repeat it");
    }

    @Test
    void armingAgainWhileStillPendingStillPrintsOnce() {
        JoinWelcome welcome = new JoinWelcome();

        welcome.arm();
        welcome.arm();

        assertTrue(welcome.consume());
        assertFalse(welcome.consume());
    }
}
