package wtf.woke.lite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.testutil.FakeModule;
import wtf.woke.lite.testutil.TestClock;

/**
 * Schema compatibility: data this build does not understand must be carried
 * across a save, and a config written by a newer build must never be
 * downgraded.
 */
class ConfigManagerSchemaTest {

    @TempDir
    Path tempDir;

    private final TestClock clock = new TestClock();

    private ConfigManager newManager(ModuleRegistry registry) {
        return new ConfigManager(registry, new ConfigIO(tempDir), clock);
    }

    private Path configFile() {
        return tempDir.resolve(ConfigIO.DEFAULT_FILE_NAME);
    }

    private void writeConfig(String json) throws IOException {
        Files.writeString(configFile(), json, StandardCharsets.UTF_8);
    }

    private static JsonObject readConfig(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static int intValueOf(QoLModule module, String settingId) {
        return ((Number) module.setting(settingId).orElseThrow().get()).intValue();
    }

    private static boolean boolValueOf(QoLModule module, String settingId) {
        return (Boolean) module.setting(settingId).orElseThrow().get();
    }

    @Test
    void unknownKeysSurviveASave() throws IOException {
        writeConfig("""
                {
                  "schemaVersion": 1,
                  "mod": "wokewtf-lite",
                  "futureTopLevel": {"a": 1},
                  "global": {"hudScale": 2.0, "futureGlobal": "keep me"},
                  "modules": {
                    "a": {
                      "enabled": true,
                      "settings": {"offsetX": 9, "futureSetting": [1, 2]},
                      "futureModuleKey": true
                    },
                    "removed.module": {"enabled": false, "settings": {"gone": 1}}
                  }
                }
                """);

        ModuleRegistry registry = new ModuleRegistry();
        registry.register(FakeModule.withSettings("a"));
        ConfigManager manager = newManager(registry);
        Setting<Double> hudScale = manager.addGlobalSetting(Settings.decimal("hudScale", 1.0, 0.5, 3.0));
        manager.load();

        assertEquals(2.0, hudScale.get().doubleValue(), "known values are applied");
        assertEquals(9, intValueOf(registry.byId("a").orElseThrow(), "offsetX"));
        assertTrue(manager.save());

        JsonObject written = readConfig(configFile());
        assertEquals(1, written.getAsJsonObject("futureTopLevel").get("a").getAsInt());
        assertEquals("keep me", written.getAsJsonObject("global").get("futureGlobal").getAsString());
        JsonObject moduleA = written.getAsJsonObject("modules").getAsJsonObject("a");
        assertEquals(2, moduleA.getAsJsonObject("settings").getAsJsonArray("futureSetting").size());
        assertTrue(moduleA.get("futureModuleKey").getAsBoolean());
        assertTrue(written.getAsJsonObject("modules").has("removed.module"),
                "an entry for a module this build does not have is preserved");
        assertEquals(9, moduleA.getAsJsonObject("settings").get("offsetX").getAsInt());
    }

    @Test
    void valuesOfTheWrongTypeAreIgnored() throws IOException {
        writeConfig("""
                {
                  "schemaVersion": 1,
                  "global": {"hudScale": "not a number"},
                  "modules": {"a": {"enabled": "yes", "settings": {"offsetX": "nope", "visible": 7}}}
                }
                """);

        ModuleRegistry registry = new ModuleRegistry();
        registry.register(FakeModule.withSettings("a"));
        ConfigManager manager = newManager(registry);
        Setting<Double> hudScale = manager.addGlobalSetting(Settings.decimal("hudScale", 1.0, 0.5, 3.0));

        assertTrue(manager.load());

        assertEquals(1.0, hudScale.get().doubleValue(), "an unusable global value keeps the default");
        assertFalse(registry.isEnabled("a"), "a non-boolean enabled flag is ignored");
        QoLModule module = registry.byId("a").orElseThrow();
        assertEquals(4, intValueOf(module, "offsetX"));
        assertTrue(boolValueOf(module, "visible"));
    }

    @Test
    void aNewerSchemaIsReadOnlyAndNeverOverwritten() throws IOException {
        String json = "{\"schemaVersion\": 99, \"global\": {\"hudScale\": 2.5}}";
        writeConfig(json);
        String before = Files.readString(configFile(), StandardCharsets.UTF_8);

        ModuleRegistry registry = new ModuleRegistry();
        registry.register(FakeModule.withSettings("a"));
        ConfigManager manager = newManager(registry);
        Setting<Double> hudScale = manager.addGlobalSetting(Settings.decimal("hudScale", 1.0, 0.5, 3.0));

        assertFalse(manager.load());
        assertTrue(manager.isReadOnly());
        assertEquals(1.0, hudScale.get().doubleValue(), "a future file is not applied");
        assertFalse(manager.save(), "and it is never rewritten");
        assertEquals(before, Files.readString(configFile(), StandardCharsets.UTF_8));

        registry.setEnabled("a", true);
        assertFalse(manager.tick(), "the debounced save respects read-only too");
        assertEquals(before, Files.readString(configFile(), StandardCharsets.UTF_8));
    }
}
