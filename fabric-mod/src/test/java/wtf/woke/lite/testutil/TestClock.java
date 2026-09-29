package wtf.woke.lite.testutil;

import java.util.function.LongSupplier;

/**
 * Hand-driven clock so time-dependent behaviour (the config autosave debounce)
 * can be tested exactly, without sleeping or racing.
 */
public final class TestClock implements LongSupplier {

    private long now;

    public TestClock() {
        this(1_000L);
    }

    public TestClock(long start) {
        this.now = start;
    }

    @Override
    public long getAsLong() {
        return now;
    }

    public void advance(long millis) {
        now += millis;
    }
}
