package wtf.woke.lite.chat;

/**
 * Bounds for how many chat lines the client keeps.
 *
 * <p>Vanilla keeps {@value #VANILLA_LENGTH} lines, and that is also the floor
 * here: the feature can only ever show <em>more</em> history, never less, so a
 * misconfigured value cannot hide something the user would otherwise have
 * seen. The ceiling exists because every kept line is retained for the whole
 * session — an unbounded value would be a slow memory leak.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 */
public final class ChatHistory {

    /** How many lines vanilla keeps, and the minimum this mod will configure. */
    public static final int VANILLA_LENGTH = 100;

    /** Smallest configurable history length. */
    public static final int MIN_LENGTH = VANILLA_LENGTH;

    /** Largest configurable history length. */
    public static final int MAX_LENGTH = 1000;

    /** History length a fresh install starts with. */
    public static final int DEFAULT_LENGTH = 300;

    private ChatHistory() {
        throw new AssertionError("No instances of " + ChatHistory.class.getName());
    }

    /**
     * @param configured the requested history length
     * @return the length to actually keep, inside {@code [MIN_LENGTH, MAX_LENGTH]}
     */
    public static int limitFor(int configured) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, configured));
    }

    /**
     * @param configured the requested history length
     * @param vanilla    the cap the game itself would use
     * @return the higher of the two, so this feature never shortens the vanilla
     *         history
     */
    public static int limitFor(int configured, int vanilla) {
        return Math.max(vanilla, limitFor(configured));
    }
}
