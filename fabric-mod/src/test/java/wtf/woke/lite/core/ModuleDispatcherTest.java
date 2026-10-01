package wtf.woke.lite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.testutil.FakeModule;

class ModuleDispatcherTest {

    /** Stand-in for a JVM-level failure, which must never be swallowed. */
    private static final class Fatal extends VirtualMachineError {
        private static final long serialVersionUID = 1L;

        Fatal(String message) {
            super(message);
        }
    }

    @Test
    void onlyActiveModulesReceiveTicks() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule enabled = new FakeModule("on");
        FakeModule disabled = new FakeModule("off");
        FakeModule unavailable = new FakeModule("nope").unavailable("wokewtf.lite.reason.singleplayer");
        registry.register(enabled);
        registry.register(disabled);
        registry.register(unavailable);
        registry.setEnabled("on", true);
        registry.setEnabled("nope", true);

        ModuleDispatcher dispatcher = new ModuleDispatcher(registry);
        dispatcher.tickAll();
        dispatcher.tickAll();

        assertEquals(2, enabled.tickCount());
        assertEquals(0, disabled.tickCount());
        assertEquals(0, unavailable.tickCount(), "an unavailable module is enabled but never run");
    }

    @Test
    void aThrowingModuleIsDisabledAndTheRestKeepRunning() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule broken = new FakeModule("broken").failOnTick();
        FakeModule healthy = new FakeModule("healthy");
        registry.register(broken);
        registry.register(healthy);
        registry.setEnabled("broken", true);
        registry.setEnabled("healthy", true);

        ModuleDispatcher dispatcher = new ModuleDispatcher(registry);
        dispatcher.tickAll();

        assertEquals(1, broken.tickCount());
        assertEquals(1, healthy.tickCount(), "one bad module must not stop the others");
        assertTrue(broken.hasFailed());
        assertFalse(broken.isEnabled());
        assertEquals(List.of("broken"), dispatcher.failedModules());

        dispatcher.tickAll();
        assertEquals(1, broken.tickCount(), "a failed module is not run again");
        assertEquals(2, healthy.tickCount());
    }

    @Test
    void failingWorldCallbacksAreIsolatedToo() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule module = new FakeModule("cb") {
            @Override
            public void onJoinWorld() {
                throw new IllegalStateException("planned world failure");
            }
        };
        registry.register(module);
        registry.setEnabled("cb", true);

        new ModuleDispatcher(registry).onJoinWorld();

        assertTrue(module.hasFailed());
        assertFalse(module.isEnabled());
    }

    @Test
    void worldEventsReachActiveModules() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule active = new FakeModule("active");
        FakeModule inactive = new FakeModule("inactive");
        registry.register(active);
        registry.register(inactive);
        registry.setEnabled("active", true);

        ModuleDispatcher dispatcher = new ModuleDispatcher(registry);
        dispatcher.onJoinWorld();
        dispatcher.onLeaveWorld();

        assertEquals(1, active.joinCount());
        assertEquals(1, active.leaveCount());
        assertEquals(0, inactive.joinCount());
        assertEquals(0, inactive.leaveCount());
    }

    @Test
    void leavingAWorldRemovesAvailabilityFromActiveModules() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule module = new FakeModule("ping");
        registry.register(module);
        registry.setEnabled("ping", true);
        assertTrue(module.isActive());

        module.unavailable("wokewtf.lite.reason.no-server");
        new ModuleDispatcher(registry).onLeaveWorld();

        assertEquals(1, module.leaveCount(), "the last world event still reaches it");
        assertFalse(module.isActive(), "afterwards it is no longer runnable");
        assertTrue(module.isEnabled());
    }

    @Test
    void enableAllAndDisableAllCountTheRealTransitions() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new FakeModule("a"));
        registry.register(new FakeModule("b"));
        ModuleDispatcher dispatcher = new ModuleDispatcher(registry);

        assertEquals(2, dispatcher.enableAll());
        assertEquals(0, dispatcher.enableAll());
        assertTrue(registry.all().stream().allMatch(QoLModule::isEnabled));

        assertEquals(2, dispatcher.disableAll());
        assertEquals(0, dispatcher.disableAll());
        assertTrue(registry.all().stream().noneMatch(QoLModule::isEnabled));
    }

    @Test
    void guardRethrowsJvmLevelFailures() {
        FakeModule module = new FakeModule("fatal");
        assertThrows(Fatal.class, () -> ModuleDispatcher.guard(module, "tick", () -> {
            throw new Fatal("out of memory, hypothetically");
        }));
        assertFalse(module.hasFailed(), "a JVM failure is not a module bug to be latched");
        assertFalse(module.isEnabled(), "and the module is left exactly as it was");
    }

    @Test
    void guardReportsWhetherTheBodyCompleted() {
        FakeModule module = new FakeModule("simple");
        assertTrue(ModuleDispatcher.guard(module, "tick", () -> { }));
        assertFalse(ModuleDispatcher.guard(module, "tick", () -> {
            throw new IllegalArgumentException("planned");
        }));
        assertTrue(module.hasFailed());
    }

    @Test
    void theDebugWatchWatchesOneModuleWithoutChangingWhatRuns() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule watched = new FakeModule("hud.fps");
        FakeModule other = new FakeModule("ui.chat");
        registry.register(watched);
        registry.register(other);
        registry.setEnabled("hud.fps", true);
        registry.setEnabled("ui.chat", true);

        ModuleDispatcher dispatcher = new ModuleDispatcher(registry);
        assertEquals("", dispatcher.debugWatch().target(), "nothing is watched until the config says so");

        dispatcher.debugWatch().watch("hud.fps");
        dispatcher.tickAll();
        dispatcher.onJoinWorld();
        dispatcher.onLeaveWorld();

        assertTrue(dispatcher.debugWatch().watches("hud.fps"));
        assertEquals(1, watched.tickCount(), "watching logs, it never gates the work");
        assertEquals(1, other.tickCount(), "an unwatched module is unaffected");
        assertEquals(1, watched.joinCount());
        assertEquals(1, watched.leaveCount());
    }
}
