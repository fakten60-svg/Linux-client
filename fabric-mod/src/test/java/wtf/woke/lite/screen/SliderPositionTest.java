package wtf.woke.lite.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the slider arithmetic the config screen draws and stores with.
 *
 * <p>The interesting cases are the ones a mouse cannot produce on purpose: a
 * click one pixel past either end, a drag over the edge, an empty range, and a
 * track with no width because the window is too narrow for one.</p>
 */
class SliderPositionTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void theEndsOfTheRangeSitAtTheEndsOfTheTrack() {
        assertEquals(0.0, SliderPosition.fractionOf(0.0, 10.0, 0.0), EPSILON);
        assertEquals(1.0, SliderPosition.fractionOf(0.0, 10.0, 10.0), EPSILON);
    }

    @Test
    void aValueInTheMiddleSitsInTheMiddle() {
        assertEquals(0.5, SliderPosition.fractionOf(0.0, 10.0, 5.0), EPSILON);
        assertEquals(0.25, SliderPosition.fractionOf(-10.0, 30.0, 0.0), EPSILON);
    }

    @Test
    void aValueOutsideTheRangeIsPinnedToTheNearestEnd() {
        assertEquals(0.0, SliderPosition.fractionOf(0.0, 10.0, -5.0), EPSILON);
        assertEquals(1.0, SliderPosition.fractionOf(0.0, 10.0, 99.0), EPSILON);
    }

    @Test
    void anEmptyOrBackwardsRangeDoesNotDivideByZero() {
        assertEquals(0.0, SliderPosition.fractionOf(4.0, 4.0, 4.0), EPSILON);
        assertEquals(0.0, SliderPosition.fractionOf(10.0, 1.0, 5.0), EPSILON);
        assertEquals(4.0, SliderPosition.valueOf(4.0, 4.0, 0.7), EPSILON);
    }

    @Test
    void theValueAtAFractionIsTheInverseOfTheFractionOfThatValue() {
        for (double fraction : new double[] {0.0, 0.1, 0.5, 0.9, 1.0}) {
            double value = SliderPosition.valueOf(3.0, 13.0, fraction);
            assertEquals(fraction, SliderPosition.fractionOf(3.0, 13.0, value), EPSILON);
        }
    }

    @Test
    void aFractionOutsideTheUnitRangeIsClamped() {
        assertEquals(5.0, SliderPosition.valueOf(5.0, 15.0, -1.0), EPSILON);
        assertEquals(15.0, SliderPosition.valueOf(5.0, 15.0, 2.0), EPSILON);
    }

    @Test
    void thePointerFractionClampsAndSurvivesAZeroWidthTrack() {
        assertEquals(0.0, SliderPosition.fractionAt(100.0, 200.0, 100.0), EPSILON);
        assertEquals(0.5, SliderPosition.fractionAt(100.0, 200.0, 200.0), EPSILON);
        assertEquals(1.0, SliderPosition.fractionAt(100.0, 200.0, 900.0), EPSILON);
        assertEquals(0.0, SliderPosition.fractionAt(100.0, 200.0, 50.0), EPSILON);
        assertEquals(0.0, SliderPosition.fractionAt(100.0, 0.0, 150.0), EPSILON);
    }

    @Test
    void aNotANumberFractionCannotProduceANotANumberValue() {
        assertEquals(0.0, SliderPosition.valueOf(0.0, 10.0, Double.NaN), EPSILON);
        assertEquals(0.0, SliderPosition.fractionAt(0.0, 10.0, Double.NaN), EPSILON);
    }
}
