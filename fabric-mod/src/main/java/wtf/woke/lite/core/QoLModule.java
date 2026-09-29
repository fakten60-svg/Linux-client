package wtf.woke.lite.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Base class for every quality-of-life module.
 *
 * <p>Invariants the rest of the framework relies on: {@link #onEnable()} and
 * {@link #onDisable()} fire only on real transitions, never twice in a row;
 * {@link #onClientTick()} runs only while the module is {@link #isActive()
 * active} (enabled, available and not failed); and a module broken by a
 * previous callback is never run again until it is explicitly re-enabled, see
 * {@link ModuleDispatcher#guard}.</p>
 *
 * <p>No Minecraft types appear here on purpose. Client-only modules render in
 * a client-side subclass, which keeps the whole framework unit-testable
 * without a game.</p>
 */
public abstract class QoLModule {

    private final List<Setting<?>> settings = new ArrayList<>(4);

    private boolean enabled;
    private boolean active;
    private boolean failed;
    private ModuleRegistry registry;
    private Runnable dirtyMarker = () -> { };

    /** @return stable unique id, e.g. {@code hud.coordinates} */
    public abstract String id();

    /** @return translation key for the module's display name */
    public abstract String translationKey();

    /** @return the category this module is grouped under */
    public abstract ModuleCategory category();

    /** @return translation key for the module's one-line description */
    public String descriptionKey() {
        return translationKey() + ".description";
    }

    // --- settings -------------------------------------------------------------

    /**
     * Declares a setting owned by this module. Call from {@link #onRegister}.
     *
     * @throws IllegalArgumentException if the id is already taken
     */
    protected final void addSetting(Setting<?> setting) {
        Objects.requireNonNull(setting, "setting");
        if (setting(setting.id()).isPresent()) {
            throw new IllegalArgumentException("duplicate setting id '" + setting.id() + "' in module " + id());
        }
        setting.addListener(() -> onSettingChanged(setting));
        settings.add(setting);
    }

    /** @return an immutable snapshot of this module's settings */
    public final List<Setting<?>> settings() {
        return List.copyOf(settings);
    }

    /** @return the setting with the given id, if declared */
    public final Optional<Setting<?>> setting(String settingId) {
        for (Setting<?> setting : settings) {
            if (setting.id().equals(settingId)) {
                return Optional.of(setting);
            }
        }
        return Optional.empty();
    }

    // --- lifecycle ------------------------------------------------------------

    /** Declares settings and keybinds. Called once, before the module can be enabled. */
    public void onRegister(ModuleRegistry registry) {
    }

    /** Called on the false → true transition. */
    public void onEnable() {
    }

    /** Called on the true → false transition. */
    public void onDisable() {
    }

    /** Per-tick work while active. */
    public void onClientTick() {
    }

    /** Called for active modules when a world becomes available. */
    public void onJoinWorld() {
    }

    /** Called for active modules when the world goes away. */
    public void onLeaveWorld() {
    }

    /** Called after one of this module's settings changed. */
    public void onSettingChanged(Setting<?> setting) {
        markDirty();
    }

    /** @return whether this module can run in the current context */
    public Availability availability() {
        return Availability.available();
    }

    // --- state ----------------------------------------------------------------

    public final boolean isEnabled() {
        return enabled;
    }

    /** @return {@code true} when the module should actually run right now */
    public final boolean isActive() {
        return active;
    }

    /** @return {@code true} if this module threw and was disabled as a result */
    public final boolean hasFailed() {
        return failed;
    }

    /** @return the registry this module was registered with, if any */
    public final Optional<ModuleRegistry> registry() {
        return Optional.ofNullable(registry);
    }

    /**
     * Enables or disables the module.
     *
     * <p>Delegates to the registry once registered so there is exactly one
     * transition path.</p>
     *
     * @return {@code true} when the requested state was applied
     */
    public final boolean setEnabled(boolean value) {
        if (registry != null) {
            return registry.setEnabled(id(), value);
        }
        return applyEnabled(value);
    }

    /** Clears the failure latch so the module can be tried again. */
    public final void clearFailure() {
        failed = false;
        refreshActive();
    }

    // --- package-private plumbing --------------------------------------------

    final void bind(ModuleRegistry owner) {
        this.registry = Objects.requireNonNull(owner, "owner");
        onRegister(owner);
        refreshActive();
    }

    final void bindDirtyMarker(Runnable marker) {
        this.dirtyMarker = Objects.requireNonNull(marker, "marker");
    }

    /** Marks the owning configuration dirty, if any. */
    protected final void markDirty() {
        dirtyMarker.run();
    }

    final boolean applyEnabled(boolean value) {
        if (enabled == value) {
            return false;
        }
        enabled = value;
        refreshActive();
        if (value) {
            failed = false;
            ModuleDispatcher.guard(this, "enable", this::onEnable);
        } else {
            ModuleDispatcher.guard(this, "disable", this::onDisable);
        }
        return true;
    }

    /** Disables after a failure without re-entering user code. */
    final void disableAfterFailure() {
        failed = true;
        enabled = false;
        active = false;
    }

    final void refreshActive() {
        active = enabled && !failed && availability().isAvailable();
    }
}
