package wtf.woke.lite.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The single owner of every module and of their enabled state.
 *
 * <p>Registration order is preserved so the config screen and the HUD render
 * in a stable order. Once {@link #freeze()} is called no further modules may be
 * registered, which turns "a module was added after the config was applied"
 * from a silent behaviour change into a startup failure.</p>
 */
public final class ModuleRegistry {

    private final Map<String, QoLModule> modules = new LinkedHashMap<>();

    private Runnable dirtyMarker = () -> { };
    private boolean frozen;

    /**
     * Registers a module and runs its {@link QoLModule#onRegister} hook.
     *
     * @throws IllegalStateException    if the registry is frozen
     * @throws IllegalArgumentException if the id is blank or already taken
     */
    public void register(QoLModule module) {
        Objects.requireNonNull(module, "module");
        if (frozen) {
            throw new IllegalStateException("cannot register '" + module.id() + "': registry is frozen");
        }
        if (module.id().isBlank()) {
            throw new IllegalArgumentException("module id must not be blank");
        }
        if (modules.containsKey(module.id())) {
            throw new IllegalArgumentException("duplicate module id '" + module.id() + "'");
        }
        module.bindDirtyMarker(dirtyMarker);
        // Bind before publishing: a module that throws while declaring its
        // settings must not be left half-registered.
        module.bind(this);
        modules.put(module.id(), module);
    }

    /** Prevents any further registration. */
    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public int size() {
        return modules.size();
    }

    /** @return every module, in registration order */
    public List<QoLModule> all() {
        return List.copyOf(modules.values());
    }

    /** @return the modules in one category, in registration order */
    public List<QoLModule> byCategory(ModuleCategory category) {
        Objects.requireNonNull(category, "category");
        return modules.values().stream().filter(module -> module.category() == category).toList();
    }

    public Optional<QoLModule> byId(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public boolean isRegistered(String id) {
        return modules.containsKey(id);
    }

    public boolean isEnabled(String id) {
        QoLModule module = modules.get(id);
        return module != null && module.isEnabled();
    }

    /**
     * Applies a desired enabled state to one module.
     *
     * @return {@code true} when the module exists and the state is now in
     *         effect; {@code false} for an unknown id
     */
    public boolean setEnabled(String id, boolean value) {
        QoLModule module = modules.get(id);
        if (module == null) {
            return false;
        }
        if (module.applyEnabled(value)) {
            markDirty();
        }
        return true;
    }

    /** @return the current enabled state of every module, in registration order */
    public Map<String, Boolean> snapshotEnabledStates() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        modules.forEach((id, module) -> states.put(id, module.isEnabled()));
        return Collections.unmodifiableMap(states);
    }

    /**
     * Applies a batch of enabled states, ignoring ids that are no longer
     * registered (an old config naming a removed module must not fail).
     *
     * @return how many modules actually changed state
     */
    public int applyEnabledStates(Map<String, Boolean> states) {
        Objects.requireNonNull(states, "states");
        int changed = 0;
        for (Map.Entry<String, Boolean> entry : states.entrySet()) {
            QoLModule module = modules.get(entry.getKey());
            if (module == null || entry.getValue() == null) {
                continue;
            }
            if (module.applyEnabled(entry.getValue())) {
                changed++;
            }
        }
        if (changed > 0) {
            markDirty();
        }
        return changed;
    }

    /** Recomputes the active flag of every module after the context changed. */
    public void refreshActivity() {
        modules.values().forEach(QoLModule::refreshActive);
    }

    /** @return modules that threw and were disabled as a result */
    public List<QoLModule> failedModules() {
        return modules.values().stream().filter(QoLModule::hasFailed).toList();
    }

    /**
     * Sets the callback invoked whenever a module's state or settings change,
     * and re-binds every already-registered module.
     */
    public void setDirtyMarker(Runnable marker) {
        this.dirtyMarker = Objects.requireNonNull(marker, "marker");
        modules.values().forEach(module -> module.bindDirtyMarker(marker));
    }

    /** Marks the configuration dirty on behalf of a batch operation. */
    void markDirtyAfterBatch() {
        markDirty();
    }

    private void markDirty() {
        dirtyMarker.run();
    }
}
