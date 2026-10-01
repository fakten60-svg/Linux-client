package wtf.woke.lite.core;

import java.util.List;
import java.util.Objects;
import wtf.woke.lite.WokeLite;

/**
 * Fans tick and world events out to active modules.
 *
 * <p>This is the module equivalent of a circuit breaker. Any module that
 * throws is logged once, latched as failed, and disabled — it can never take
 * down the tick loop, so one broken feature cannot break the client or the
 * other modules. {@link #guard} is shared with {@link ModuleRegistry} so the
 * enable/disable transitions get exactly the same protection.</p>
 *
 * <p>One module can be watched in detail at a time, through
 * {@link #debugWatch()}. That is how a module that looks dead is told apart
 * from one that is merely off: the watched id gets a line saying whether it was
 * active and enabled at each event, whether or not it does anything.</p>
 */
public final class ModuleDispatcher {

    private final ModuleRegistry registry;
    private final DebugWatch debugWatch = new DebugWatch();

    public ModuleDispatcher(ModuleRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** The one-module watch the config's {@code debugModule} setting drives. */
    public DebugWatch debugWatch() {
        return debugWatch;
    }

    /** Runs {@link QoLModule#onClientTick()} on every active module. */
    public void tickAll() {
        for (QoLModule module : registry.all()) {
            boolean watched = debugWatch.watches(module.id());
            if (module.isActive()) {
                if (watched && debugWatch.tickLineDue()) {
                    WokeLite.LOGGER.debug("watch '{}' tick: enabled={}, active=true",
                            module.id(), module.isEnabled());
                }
                guard(module, "tick", module::onClientTick);
            } else if (watched && debugWatch.tickLineDue()) {
                WokeLite.LOGGER.debug("watch '{}' tick: enabled={}, active=false",
                        module.id(), module.isEnabled());
            }
        }
    }

    /** Notifies active modules that a world became available. */
    public void onJoinWorld() {
        registry.refreshActivity();
        for (QoLModule module : registry.all()) {
            if (debugWatch.watches(module.id())) {
                WokeLite.LOGGER.debug("watch '{}' world join: enabled={}, active={}",
                        module.id(), module.isEnabled(), module.isActive());
            }
            if (module.isActive()) {
                guard(module, "world join", module::onJoinWorld);
            }
        }
    }

    /** Notifies active modules that the world went away, then re-evaluates. */
    public void onLeaveWorld() {
        for (QoLModule module : registry.all()) {
            if (debugWatch.watches(module.id())) {
                WokeLite.LOGGER.debug("watch '{}' world leave: enabled={}, active={}",
                        module.id(), module.isEnabled(), module.isActive());
            }
            if (module.isActive()) {
                guard(module, "world leave", module::onLeaveWorld);
            }
        }
        registry.refreshActivity();
    }

    /** @return how many modules changed state */
    public int enableAll() {
        return applyEnabledToAll(true);
    }

    /** @return how many modules changed state */
    public int disableAll() {
        return applyEnabledToAll(false);
    }

    /** @return ids of modules currently latched as failed */
    public List<String> failedModules() {
        return registry.failedModules().stream().map(QoLModule::id).toList();
    }

    /**
     * Runs {@code body}, isolating any failure it produces.
     *
     * <p>{@link VirtualMachineError} is deliberately rethrown: a module must
     * not be able to swallow a JVM-level failure and keep the client running
     * in an undefined state.</p>
     *
     * @return {@code true} when the body completed normally
     */
    public static boolean guard(QoLModule module, String phase, Runnable body) {
        try {
            body.run();
            return true;
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            WokeLite.LOGGER.error("Module '{}' threw during {}; disabling it", module.id(), phase, failure);
            module.disableAfterFailure();
            return false;
        }
    }

    private int applyEnabledToAll(boolean value) {
        int changed = 0;
        for (QoLModule module : registry.all()) {
            if (module.applyEnabled(value)) {
                changed++;
            }
        }
        if (changed > 0) {
            registry.markDirtyAfterBatch();
        }
        return changed;
    }
}
