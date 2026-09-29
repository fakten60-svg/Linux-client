package wtf.woke.lite.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.testutil.FakeModule;
import wtf.woke.lite.testutil.TestClock;

/**
 * The autosave is a debounce, not a per-change write: bursts of changes inside
 * the window must collapse into one write, and a clean config must never be
 * rewritten.
 */
class ConfigManagerAutosaveTest {

    @TempDir
    Path tempDir;

    private final TestClock clock = new TestClock();

    private ConfigManager newManager(ModuleRegistry registry) {
        return new ConfigManager(registry, new ConfigIO(tempDir), clock);
    }

    private Path configFile() {
        return tempDir.resolve(ConfigIO.DEFAULT_FILE_NAME);
    }

    private ModuleRegistry registryWith(String moduleId) {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(FakeModule.withSettings(moduleId));
        return registry;
    }

    @Test
    void autosaveWaitsForTheDebounceWindow() {
        ModuleRegistry registry = registryWith("a");
        ConfigManager manager = newManager(registry);
        manager.setAutoSaveMillis(5_000);
        manager.load();

        assertFalse(manager.tick(), "nothing is pending yet");

        registry.setEnabled("a", true);
        assertFalse(manager.tick());
        assertFalse(Files.exists(configFile()), "the write is deferred");

        clock.advance(4_999);
        assertFalse(manager.tick());
        assertFalse(Files.exists(configFile()));

        clock.advance(1);
        assertTrue(manager.tick());
        assertTrue(Files.exists(configFile()));
        assertFalse(manager.isDirty(), "a successful save clears the pending flag");

        assertFalse(manager.tick(), "and a clean config is never rewritten");
    }

    @Test
    void manyChangesInsideTheWindowStillProduceOneWrite() {
        ModuleRegistry registry = registryWith("a");
        ConfigManager manager = newManager(registry);
        manager.setAutoSaveMillis(1_000);
        manager.load();

        registry.setEnabled("a", true);
        clock.advance(200);
        registry.setEnabled("a", false);
        clock.advance(200);
        registry.setEnabled("a", true);

        assertFalse(manager.tick(), "still inside the window measured from the first change");
        clock.advance(1_000);
        assertTrue(manager.tick());
    }

    @Test
    void aZeroDebounceSavesOnTheNextTick() {
        ModuleRegistry registry = registryWith("a");
        ConfigManager manager = newManager(registry);
        manager.setAutoSaveMillis(0);
        manager.load();

        registry.setEnabled("a", true);

        assertTrue(manager.tick());
        assertTrue(Files.exists(configFile()));
    }
}
