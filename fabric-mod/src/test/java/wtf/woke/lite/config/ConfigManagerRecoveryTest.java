package wtf.woke.lite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.testutil.FakeModule;
import wtf.woke.lite.testutil.TestClock;

/** What happens when the file on disk is broken, and that saving is stable. */
class ConfigManagerRecoveryTest {

    @TempDir
    Path tempDir;

    private final TestClock clock = new TestClock();

    private ConfigManager newManager(ModuleRegistry registry) {
        return new ConfigManager(registry, new ConfigIO(tempDir), clock);
    }

    private Path configFile() {
        return tempDir.resolve(ConfigIO.DEFAULT_FILE_NAME);
    }

    @Test
    void aCorruptFileIsQuarantinedAndTheNextSaveStartsFresh() throws IOException {
        Files.writeString(configFile(), "{\"schemaVersion\": 1, }", StandardCharsets.UTF_8);
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(FakeModule.withSettings("a"));
        ConfigManager manager = newManager(registry);

        assertFalse(manager.load());
        assertEquals(ConfigIO.Status.CORRUPT, manager.lastStatus());
        assertFalse(manager.isReadOnly(), "corruption is recoverable, it is not read-only");
        try (Stream<Path> entries = Files.list(tempDir)) {
            assertEquals(1, entries.filter(path -> path.getFileName().toString().contains(".corrupt-")).count());
        }

        registry.setEnabled("a", true);
        assertTrue(manager.save());

        String written = Files.readString(configFile(), StandardCharsets.UTF_8);
        assertTrue(written.contains("\"schemaVersion\": 1"));
        assertTrue(written.contains("\"enabled\": true"));
    }

    @Test
    void savingIsIdempotentAcrossLoads() throws IOException {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(FakeModule.withSettings("a"));
        ConfigManager manager = newManager(registry);
        manager.load();
        registry.setEnabled("a", true);
        manager.save();
        String first = Files.readString(configFile(), StandardCharsets.UTF_8);

        manager.markDirty();
        assertTrue(manager.save());
        assertEquals(first, Files.readString(configFile(), StandardCharsets.UTF_8),
                "no state changed, so the bytes are unchanged");
        assertEquals("wokewtf-lite",
                JsonParser.parseString(first).getAsJsonObject().get("mod").getAsString());
    }
}
