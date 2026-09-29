package wtf.woke.lite.reconnect;

/**
 * The reconnect sequence as a state machine, with no Minecraft in sight.
 *
 * <p>Three properties matter here and each one is a test: the countdown runs
 * down in ticks and fires exactly once when it reaches zero, a second start
 * while an attempt is in flight is refused rather than queued, and a cancelled
 * countdown can never produce a connection. Keeping the sequence out of the
 * client means all three hold without a game instance, which is the only way
 * they can be checked at all.</p>
 *
 * <p>The caller drives it: {@link #start} when a session ends, {@link #tick}
 * once per client tick, {@link #cancel} when the player says no, and
 * {@link #markConnected} once the join actually happened. A connect attempt
 * that neither succeeds nor fails within {@link #start}'s timeout is given up
 * on — the mod reconnects once and never loops, because a retry loop is exactly
 * what turning a ban into a connectivity problem would look like.</p>
 */
public final class ReconnectStateMachine {

    /** How long one connect attempt may take before the sequence is dropped. */
    public static final int DEFAULT_ATTEMPT_TIMEOUT_TICKS = 20 * 20;

    /** What one {@link #tick()} did, so the caller knows whether to act. */
    public enum Step {
        /** Nothing to do this tick. */
        WAITING,

        /** The countdown is still running. */
        COUNTING,

        /** The countdown reached zero on this tick: open the connect screen now. */
        CONNECT,

        /** The attempt ran into its timeout without a join; the sequence is over. */
        TIMED_OUT
    }

    private ReconnectState state = ReconnectState.IDLE;
    private int remainingTicks;
    private int attemptTimeoutTicks = DEFAULT_ATTEMPT_TIMEOUT_TICKS;
    private int attemptTicksLeft;

    public ReconnectState state() {
        return state;
    }

    public boolean isIdle() {
        return state == ReconnectState.IDLE;
    }

    public boolean isCounting() {
        return state == ReconnectState.COUNTING;
    }

    public boolean isConnecting() {
        return state == ReconnectState.CONNECTING;
    }

    /** @return ticks left on the countdown, zero when not counting */
    public int remainingTicks() {
        return remainingTicks;
    }

    /** @return the countdown rounded up to whole seconds, zero when not counting */
    public int remainingSeconds() {
        return (remainingTicks + 19) / 20;
    }

    /**
     * Arms the countdown.
     *
     * @param countdownTicks      ticks before the connect is opened; at least 1
     * @param attemptTimeoutTicks ticks an attempt may take; at least 1
     * @return {@code false} when a countdown or an attempt is already running,
     *         which is what keeps a duplicate disconnect event from arming a
     *         second connection
     */
    public boolean start(int countdownTicks, int attemptTimeoutTicks) {
        if (countdownTicks < 1 || attemptTimeoutTicks < 1) {
            throw new IllegalArgumentException("countdown and timeout must be at least one tick");
        }
        if (state == ReconnectState.COUNTING || state == ReconnectState.CONNECTING) {
            return false;
        }
        state = ReconnectState.COUNTING;
        remainingTicks = countdownTicks;
        this.attemptTimeoutTicks = attemptTimeoutTicks;
        attemptTicksLeft = 0;
        return true;
    }

    /**
     * Advances the sequence by one tick.
     *
     * @return {@link Step#CONNECT} exactly once per attempt, on the tick the
     *         countdown reaches zero
     */
    public Step tick() {
        switch (state) {
            case COUNTING:
                remainingTicks--;
                if (remainingTicks > 0) {
                    return Step.COUNTING;
                }
                remainingTicks = 0;
                state = ReconnectState.CONNECTING;
                attemptTicksLeft = attemptTimeoutTicks;
                return Step.CONNECT;
            case CONNECTING:
                attemptTicksLeft--;
                if (attemptTicksLeft > 0) {
                    return Step.WAITING;
                }
                state = ReconnectState.IDLE;
                return Step.TIMED_OUT;
            default:
                return Step.WAITING;
        }
    }

    /**
     * Stops a running countdown or attempt.
     *
     * @return {@code true} when there was something to cancel
     */
    public boolean cancel() {
        if (state != ReconnectState.COUNTING && state != ReconnectState.CONNECTING) {
            return false;
        }
        state = ReconnectState.CANCELLED;
        remainingTicks = 0;
        attemptTicksLeft = 0;
        return true;
    }

    /**
     * Records that the join succeeded, so the sequence is over.
     *
     * @return {@code true} when this ended a pending attempt
     */
    public boolean markConnected() {
        boolean pending = state == ReconnectState.COUNTING || state == ReconnectState.CONNECTING;
        state = ReconnectState.IDLE;
        remainingTicks = 0;
        attemptTicksLeft = 0;
        return pending;
    }

    /** Forgets everything, including a previous cancellation. */
    public void reset() {
        markConnected();
    }
}
