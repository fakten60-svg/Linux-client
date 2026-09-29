package wtf.woke.lite.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Whether a module can run right now, and if not, why not.
 *
 * <p>A module stays {@link QoLModule#isEnabled() enabled} while it is
 * unavailable — the user's choice is theirs — but stops being
 * {@link QoLModule#isActive() active}, so nothing in the dispatcher runs it.
 * The reason is a translation key shown in the config screen and in logs.</p>
 */
public final class Availability {

    private static final Availability AVAILABLE = new Availability(null);

    private final String reasonKey;

    private Availability(String reasonKey) {
        this.reasonKey = reasonKey;
    }

    /** @return the shared "runs anywhere" instance */
    public static Availability available() {
        return AVAILABLE;
    }

    /**
     * @param reasonKey translation key explaining why the module cannot run,
     *                  e.g. {@code wokewtf.lite.reason.singleplayer}
     * @return an unavailable marker carrying that reason
     */
    public static Availability unavailable(String reasonKey) {
        return new Availability(Objects.requireNonNull(reasonKey, "reasonKey"));
    }

    /** @return {@code true} when the module may run */
    public boolean isAvailable() {
        return reasonKey == null;
    }

    /** @return the reason when unavailable, otherwise empty */
    public Optional<String> reasonKey() {
        return Optional.ofNullable(reasonKey);
    }

    @Override
    public String toString() {
        return reasonKey == null ? "available" : "unavailable(" + reasonKey + ")";
    }
}
