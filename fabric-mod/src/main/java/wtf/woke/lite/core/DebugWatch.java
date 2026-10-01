package wtf.woke.lite.core;

/**
 * Which single module the dispatcher logs in detail, and how often.
 *
 * <p>Kept out of {@link ModuleDispatcher} so the rule behind the throttling is
 * a plain, testable thing rather than a side effect of the tick loop. One
 * module at a time is deliberate: the point is to watch the module that is
 * behaving oddly, and a switch that turned on logging for all thirteen would
 * bury the line you are looking for.</p>
 *
 * <p>Tick lines are throttled because a client ticks twenty times a second: a
 * line per tick would be two hundred lines a minute of the same sentence. World
 * events are rare enough to always be worth a line, so they are never held
 * back.</p>
 */
public final class DebugWatch {

    /** Tick callbacks between two tick lines for the watched module. */
    public static final int TICK_LOG_INTERVAL = 100;

    private String target = "";
    private int ticks;

    /**
     * Points the watch at one module id, or at nothing at all.
     *
     * <p>Blank, {@code null} and an id no module answers to all end up watching
     * nothing: a typo should switch the logging off, not log every module.</p>
     *
     * @param moduleId the id to watch, or blank to stop watching
     */
    public void watch(String moduleId) {
        String next = moduleId == null ? "" : moduleId.trim();
        if (!next.equals(target)) {
            target = next;
            ticks = 0;
        }
    }

    /** @return the watched module id, empty when nothing is watched */
    public String target() {
        return target;
    }

    /** @return {@code true} when {@code moduleId} is the one being watched */
    public boolean watches(String moduleId) {
        return !target.isEmpty() && target.equals(moduleId);
    }

    /**
     * Counts one tick of the watched module and decides whether to log it.
     *
     * <p>The first tick after a change is always logged, so turning the watch on
     * answers "is this thing ticking at all?" without waiting.</p>
     *
     * @return {@code true} when a tick line is due
     */
    public boolean tickLineDue() {
        if (target.isEmpty()) {
            return false;
        }
        ticks++;
        return ticks == 1 || ticks % TICK_LOG_INTERVAL == 0;
    }
}
