package wtf.woke.lite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.testutil.FakeModule;

class QoLModuleTest {

    @Test
    void enableAndDisableTransitionsFireExactlyOnce() {
        FakeModule module = new FakeModule("test.lifecycle");

        assertTrue(module.setEnabled(true));
        assertFalse(module.setEnabled(true), "a repeated request is not a transition");
        assertEquals(1, module.enableCount());

        assertTrue(module.setEnabled(false));
        assertFalse(module.setEnabled(false));
        assertEquals(1, module.disableCount());
    }

    @Test
    void activeFollowsEnabledAvailabilityAndFailures() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule module = new FakeModule("test.active");
        registry.register(module);
        assertFalse(module.isActive(), "starts disabled");

        module.setEnabled(true);
        assertTrue(module.isActive());

        module.unavailable("wokewtf.lite.reason.singleplayer");
        registry.refreshActivity();
        assertTrue(module.isEnabled(), "the user's choice survives unavailability");
        assertFalse(module.isActive(), "but nothing may run");

        module.available();
        registry.refreshActivity();
        assertTrue(module.isActive());
    }

    @Test
    void registeredModulesDelegateEnableRequestsToTheRegistry() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule module = new FakeModule("test.delegates");
        registry.register(module);

        module.setEnabled(true);
        assertTrue(registry.isEnabled("test.delegates"));

        registry.setEnabled("test.delegates", false);
        assertFalse(module.isEnabled());
        assertEquals(1, module.disableCount());
    }

    @Test
    void unregisteredModulesCanStillBeToggledLocally() {
        FakeModule module = new FakeModule("test.local");
        assertTrue(module.registry().isEmpty());
        assertTrue(module.setEnabled(true));
        assertTrue(module.isActive());
    }

    @Test
    void settingsAreDeclaredOnceAndLookedUpById() {
        QoLModule module = new FakeModule("test.settings") {
            @Override
            public void onRegister(ModuleRegistry registry) {
                addSetting(Settings.bool("visible", true));
            }
        };
        new ModuleRegistry().register(module);

        assertEquals(1, module.settings().size());
        assertEquals("visible", module.setting("visible").orElseThrow().id());
        assertTrue(module.setting("absent").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> module.settings().clear());
    }

    @Test
    void duplicateSettingIdsAreRejected() {
        QoLModule module = new FakeModule("test.duplicates") {
            @Override
            public void onRegister(ModuleRegistry registry) {
                addSetting(Settings.bool("flag", true));
                addSetting(Settings.bool("flag", false));
            }
        };
        ModuleRegistry registry = new ModuleRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(module));
        assertEquals(0, registry.size(), "a module that failed to declare settings is not registered");
    }

    @Test
    void changingASettingNotifiesTheModuleAndMarksTheConfigDirty() {
        AtomicInteger dirtyCalls = new AtomicInteger();
        AtomicInteger settingChanges = new AtomicInteger();

        QoLModule module = new FakeModule("test.notify") {
            @Override
            public void onRegister(ModuleRegistry registry) {
                addSetting(Settings.integer("offsetX", 4, 0, 100));
            }

            @Override
            public void onSettingChanged(Setting<?> setting) {
                settingChanges.incrementAndGet();
                super.onSettingChanged(setting);
            }
        };

        ModuleRegistry registry = new ModuleRegistry();
        registry.setDirtyMarker(dirtyCalls::incrementAndGet);
        registry.register(module);

        Setting<?> setting = module.setting("offsetX").orElseThrow();
        setting.fromRaw(9);

        assertEquals(1, settingChanges.get());
        assertEquals(1, dirtyCalls.get());
    }

    @Test
    void aFailingEnableLatchesTheModuleOff() {
        ModuleRegistry registry = new ModuleRegistry();
        FakeModule module = new FakeModule("test.broken").failOnEnable();
        registry.register(module);

        assertTrue(registry.setEnabled("test.broken", true));
        assertTrue(module.hasFailed());
        assertFalse(module.isEnabled(), "a module that threw while enabling must not stay on");
        assertFalse(module.isActive());

        module.failOnEnable(false).clearFailure();
        assertFalse(module.hasFailed());
        assertTrue(module.setEnabled(true), "clearing the latch allows another attempt");
        assertTrue(module.isActive());
    }

    @Test
    void settingsSnapshotIsAnImmutableCopy() {
        QoLModule module = FakeModule.withSettings("test.snapshot");
        new ModuleRegistry().register(module);

        List<Setting<?>> settings = module.settings();
        assertEquals(8, settings.size(), "one setting of every type is declared");
        assertThrows(UnsupportedOperationException.class, () -> settings.add(Settings.bool("extra", true)));
    }
}
