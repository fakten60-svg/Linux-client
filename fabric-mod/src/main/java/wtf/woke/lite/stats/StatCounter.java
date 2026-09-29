package wtf.woke.lite.stats;

/**
 * Accumulates the local counters while the mod is running.
 *
 * <p>No Minecraft types: the client hands it ticks and positions, and every
 * rule that turns those into numbers lives here, where it can be tested without
 * a world. The two rules worth stating are the teleport guard and the position
 * reset — a player who is moved by a command, a portal or a server teleport
 * travels hundreds of blocks in one tick, and counting that as walking would
 * make the number meaningless; and a world change puts the player somewhere
 * unrelated, so the previous position must be forgotten rather than measured
 * against.</p>
 *
 * <p>This is a local tally and nothing else. It is never sent to a server, and
 * it never leaves this machine.</p>
 */
public final class StatCounter {

    /**
     * Movement per tick beyond which the step is not walking.
     *
     * <p>Well above any legitimate sprint or fall step (a player covers at most
     * a few blocks per tick) and well below a teleport, so the two cannot be
     * confused in either direction.</p>
     */
    public static final double TELEPORT_THRESHOLD = 16.0;

    private long blocksMined;
    private double distanceWalked;
    private long playTimeTicks;
    private boolean hasPosition;
    private double lastX;
    private double lastZ;

    /** Counts one tick of playtime. */
    public void addTick() {
        playTimeTicks++;
    }

    /** Counts one block this client saw itself finish breaking. */
    public void addBlockMined() {
        blocksMined++;
    }

    /**
     * Accounts for the player being at a horizontal position this tick.
     *
     * @return the distance added, {@code 0.0} for the first fix after a reset
     *         or for a step too large to be walking
     */
    public double addMovement(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return 0.0;
        }
        if (!hasPosition) {
            remember(x, z);
            return 0.0;
        }
        double dx = x - lastX;
        double dz = z - lastZ;
        remember(x, z);
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > TELEPORT_THRESHOLD) {
            return 0.0;
        }
        distanceWalked += distance;
        return distance;
    }

    /** Forgets the last position, so a world change is not measured as a step. */
    public void forgetPosition() {
        hasPosition = false;
    }

    /** @return the counters as they stand, without changing them */
    public StatSnapshot snapshot() {
        return new StatSnapshot(blocksMined, distanceWalked, playTimeTicks);
    }

    /** Replaces every counter with the stored values. */
    public void restore(StatSnapshot snapshot) {
        if (snapshot == null) {
            reset();
            return;
        }
        blocksMined = snapshot.blocksMined();
        distanceWalked = snapshot.distanceWalked();
        playTimeTicks = snapshot.playTimeTicks();
        forgetPosition();
    }

    /** Returns every counter to zero and forgets the last position. */
    public void reset() {
        blocksMined = 0L;
        distanceWalked = 0.0;
        playTimeTicks = 0L;
        forgetPosition();
    }

    private void remember(double x, double z) {
        lastX = x;
        lastZ = z;
        hasPosition = true;
    }
}
