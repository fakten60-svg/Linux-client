package wtf.woke.lite.stats;

/**
 * The counters as the config stores them.
 *
 * <p>A separate immutable value on purpose: it is what crosses the boundary
 * between the counter (which lives in memory while the module runs) and the
 * config (which outlives the session), so the counter never needs to know how a
 * number is written down and the config never needs to know how one is
 * counted.</p>
 *
 * <p>Everything here is local to this client. Nothing is sent anywhere, there
 * is no ranking, and no server is told any of it.</p>
 *
 * @param blocksMined    blocks this client saw itself finish breaking
 * @param distanceWalked horizontal blocks moved, teleports excluded
 * @param playTimeTicks  ticks spent with a world loaded
 */
public record StatSnapshot(long blocksMined, double distanceWalked, long playTimeTicks) {

    /** Ticks in one second, which is how playtime is reported. */
    public static final int TICKS_PER_SECOND = 20;

    /** The counters a fresh install starts from. */
    public static final StatSnapshot EMPTY = new StatSnapshot(0L, 0.0, 0L);

    public StatSnapshot {
        blocksMined = Math.max(0L, blocksMined);
        distanceWalked = Double.isFinite(distanceWalked) ? Math.max(0.0, distanceWalked) : 0.0;
        playTimeTicks = Math.max(0L, playTimeTicks);
    }

    /** @return playtime rounded down to whole seconds */
    public long playTimeSeconds() {
        return playTimeTicks / TICKS_PER_SECOND;
    }

    /** @return whether every counter is still at its starting value */
    public boolean isEmpty() {
        return blocksMined == 0L && distanceWalked == 0.0 && playTimeTicks == 0L;
    }
}
