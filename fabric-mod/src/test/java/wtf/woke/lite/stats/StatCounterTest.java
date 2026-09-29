package wtf.woke.lite.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the two counting rules that decide whether the numbers mean anything:
 * distance only accrues between consecutive positions of the same session, and
 * a jump too large to be a step never counts as walking.
 */
class StatCounterTest {

    private static final double EPSILON = 1e-9;

    private final StatCounter counter = new StatCounter();

    @Test
    void countsTicksAndBlocks() {
        counter.addTick();
        counter.addTick();
        counter.addBlockMined();

        StatSnapshot snapshot = counter.snapshot();
        assertEquals(2L, snapshot.playTimeTicks());
        assertEquals(1L, snapshot.blocksMined());
        assertEquals(0.0, snapshot.distanceWalked(), EPSILON);
    }

    @Test
    void theFirstPositionIsAPlaceToMeasureFromNotAWalk() {
        assertEquals(0.0, counter.addMovement(100.0, -40.0), EPSILON);
        assertEquals(0.0, counter.snapshot().distanceWalked(), EPSILON);
    }

    @Test
    void accumulatesStraightAndDiagonalSteps() {
        counter.addMovement(0.0, 0.0);
        assertEquals(3.0, counter.addMovement(3.0, 0.0), EPSILON);   // straight along x
        assertEquals(4.0, counter.addMovement(3.0, 4.0), EPSILON);   // straight along z
        assertEquals(5.0, counter.addMovement(6.0, 8.0), EPSILON);   // the 3-4-5 diagonal
        assertEquals(12.0, counter.snapshot().distanceWalked(), EPSILON);
    }

    @Test
    void aTeleportIsNotAWalk() {
        counter.addMovement(0.0, 0.0);

        assertEquals(0.0, counter.addMovement(1_000.0, 1_000.0), EPSILON);
        assertEquals(0.0, counter.snapshot().distanceWalked(), EPSILON);

        // ... and the position moved, so the next real step is measured from there.
        assertEquals(1.0, counter.addMovement(1_001.0, 1_000.0), EPSILON);
    }

    @Test
    void aStepRightAtTheThresholdStillCounts() {
        counter.addMovement(0.0, 0.0);
        assertEquals(StatCounter.TELEPORT_THRESHOLD,
                counter.addMovement(StatCounter.TELEPORT_THRESHOLD, 0.0), EPSILON);
    }

    @Test
    void forgettingThePositionStopsAWorldChangeFromBeingAWalk() {
        counter.addMovement(0.0, 0.0);
        counter.forgetPosition();

        assertEquals(0.0, counter.addMovement(500.0, 500.0), EPSILON);
        assertEquals(0.0, counter.snapshot().distanceWalked(), EPSILON);
    }

    @Test
    void ignoresNonFinitePositions() {
        counter.addMovement(0.0, 0.0);
        assertEquals(0.0, counter.addMovement(Double.NaN, 0.0), EPSILON);
        assertEquals(0.0, counter.addMovement(0.0, Double.POSITIVE_INFINITY), EPSILON);
        assertEquals(0.0, counter.snapshot().distanceWalked(), EPSILON);
    }

    @Test
    void restoreReplacesEveryCounter() {
        counter.addTick();
        counter.addBlockMined();
        counter.addMovement(0.0, 0.0);

        counter.restore(new StatSnapshot(7L, 12.5, 400L));

        StatSnapshot snapshot = counter.snapshot();
        assertEquals(7L, snapshot.blocksMined());
        assertEquals(12.5, snapshot.distanceWalked(), EPSILON);
        assertEquals(400L, snapshot.playTimeTicks());
    }

    @Test
    void restoreForgetsThePreviousPosition() {
        counter.addMovement(0.0, 0.0);
        counter.restore(new StatSnapshot(0L, 0.0, 0L));

        assertEquals(0.0, counter.addMovement(900.0, 0.0), EPSILON);
        assertEquals(0.0, counter.snapshot().distanceWalked(), EPSILON);
    }

    @Test
    void resetZeroesEverything() {
        counter.addTick();
        counter.addBlockMined();
        counter.addMovement(0.0, 0.0);
        counter.addMovement(2.0, 0.0);

        counter.reset();

        assertTrue(counter.snapshot().isEmpty());
        assertEquals(StatSnapshot.EMPTY, counter.snapshot());
    }

    @Test
    void playtimeIsReportedInWholeSeconds() {
        for (int tick = 0; tick < 39; tick++) {
            counter.addTick();
        }
        assertEquals(1L, counter.snapshot().playTimeSeconds());

        counter.addTick();
        assertEquals(2L, counter.snapshot().playTimeSeconds());
    }

    @Test
    void aSnapshotRejectsNonsenseValues() {
        StatSnapshot snapshot = new StatSnapshot(-5L, -2.0, -1L);

        assertEquals(0L, snapshot.blocksMined());
        assertEquals(0.0, snapshot.distanceWalked(), EPSILON);
        assertEquals(0L, snapshot.playTimeTicks());
        assertTrue(snapshot.isEmpty());
        assertFalse(new StatSnapshot(1L, 0.0, 0L).isEmpty());
    }
}
