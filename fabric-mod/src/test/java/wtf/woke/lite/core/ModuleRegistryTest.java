package wtf.woke.lite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.testutil.FakeModule;

class ModuleRegistryTest {

    @Test
    void modulesKeepTheirRegistrationOrder() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new FakeModule("c"));
        registry.register(new FakeModule("a"));
        registry.register(new FakeModule("b"));

        assertEquals(List.of("c", "a", "b"), registry.all().stream().map(QoLModule::id).toList());
        assertEquals(3, registry.size());
        assertTrue(registry.isRegistered("a"));
        assertFalse(registry.isRegistered("z"));
    }

    @Test
    void duplicateAndBlankIdsAreRejected() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new FakeModule("dup"));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new FakeModule("dup")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new FakeModule("  ")));
        assertEquals(1, registry.size());
    }

    @Test
    void frozenRegistriesRejectNewModules() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new FakeModule("first"));
        registry.freeze();

        assertTrue(registry.isFrozen());
        assertThrows(IllegalStateException.class, () -> registry.register(new FakeModule("late")));
    }

    @Test
    void lookupByIdAndByCategory() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new FakeModule("hud.one", ModuleCategory.HUD));
        registry.register(new FakeModule("ui.one", ModuleCategory.INTERFACE));
        registry.register(new FakeModule("hud.two", ModuleCategory.HUD));

        assertEquals("hud.one", registry.byId("hud.one").orElseThrow().id());
        assertTrue(registry.byId("missing").isEmpty());
        assertEquals(List.of("hud.one", "hud.two"),
                registry.byCategory(ModuleCategory.HUD).stream().map(QoLModule::id).toList());
        assertTrue(registry.byCategory(ModuleCategory.CONVENIENCE).isEmpty());
    }

    @Test
    void setEnabledReportsUnknownIdsAndIsIdempotent() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule module = new FakeModule("one");
        registry.register(module);

        assertFalse(registry.setEnabled("missing", true));
        assertTrue(registry.setEnabled("one", true));
        assertTrue(registry.setEnabled("one", true));
        assertEquals(1, module.enableCount(), "the repetition is not a transition");
        assertTrue(registry.isEnabled("one"));
        assertFalse(registry.isEnabled("missing"));
    }

    @Test
    void enabledStatesRoundTripThroughASnapshot() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new FakeModule("a"));
        registry.register(new FakeModule("b"));
        registry.register(new FakeModule("c"));
        registry.setEnabled("a", true);
        registry.setEnabled("c", true);

        Map<String, Boolean> snapshot = registry.snapshotEnabledStates();
        assertEquals(Map.of("a", true, "b", false, "c", true), snapshot);

        registry.applyEnabledStates(Map.of("a", false, "b", false, "c", false));
        assertTrue(registry.all().stream().noneMatch(QoLModule::isEnabled));

        assertEquals(2, registry.applyEnabledStates(snapshot), "only the two real transitions count");
        assertEquals(snapshot, registry.snapshotEnabledStates());
    }

    @Test
    void batchApplyIgnoresIdsThatNoLongerExist() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new FakeModule("kept"));

        Map<String, Boolean> stale = new LinkedHashMap<>();
        stale.put("removed-in-a-later-build", true);
        stale.put("kept", true);
        stale.put(null, true);

        assertEquals(1, registry.applyEnabledStates(stale));
        assertTrue(registry.isEnabled("kept"));
    }

    @Test
    void dirtyMarkerCoversModulesRegisteredLater() {
        AtomicInteger dirty = new AtomicInteger();
        ModuleRegistry registry = new ModuleRegistry();
        registry.setDirtyMarker(dirty::incrementAndGet);

        FakeModule module = FakeModule.withSettings("late");
        registry.register(module);
        module.setting("visible").orElseThrow().fromRaw(false);

        assertEquals(1, dirty.get(), "a module registered after the marker was set still reports changes");
    }

    @Test
    void stateChangesMarkTheConfigDirty() {
        AtomicInteger dirty = new AtomicInteger();
        ModuleRegistry registry = new ModuleRegistry();
        registry.setDirtyMarker(dirty::incrementAndGet);
        registry.register(new FakeModule("one"));

        registry.setEnabled("one", true);
        registry.setEnabled("one", true);
        assertEquals(1, dirty.get(), "only the real transition is a change");

        registry.setEnabled("missing", true);
        assertEquals(1, dirty.get());
    }

    @Test
    void failedModulesAreListed() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule module = new FakeModule("broken").failOnEnable();
        registry.register(module);
        registry.setEnabled("broken", true);

        assertEquals(List.of("broken"), registry.failedModules().stream().map(QoLModule::id).toList());
        assertTrue(registry.setEnabled("broken", true));
        assertFalse(registry.isEnabled("broken"));
    }
}
