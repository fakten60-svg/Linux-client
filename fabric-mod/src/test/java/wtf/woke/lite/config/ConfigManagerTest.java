package wtf.woke.lite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.testutil.FakeModule;
import wtf.woke.lite.testutil.TestClock;

/** The happy path: reading a config, applying it, and writing it back. */
class ConfigManagerTest {

    @TempDir
    Path tempDir;

    private final TestClock clock = new TestClock();

    private ConfigManager newManager(ModuleRegistry registry) {
        return new ConfigManager(registry, new ConfigIO(tempDir), clock);
    }

    private Path configFile() {
        return tempDir.resolve(ConfigIO.DEFAULT_FILE_NAME);
    }

    private static Object valueOf(QoLModule module, String settingId) {
        return module.setting(settingId).orElseThrow().get();
    }

    private static int intValueOf(QoLModule module, String settingId) {
        return ((Number) valueOf(module, settingId)).intValue();
    }

    private static boolean boolValueOf(QoLModule module, String settingId) {
        return (Boolean) valueOf(module, settingId);
    }

    private static JsonObject readConfig(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void firstRunReportsMissingAndTheFirstSaveCreatesTheFile() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(FakeModule.withSettings("a"));
        ConfigManager manager = newManager(registry);

        assertFalse(manager.load());
        assertEquals(ConfigIO.Status.MISSING, manager.lastStatus());
        assertFalse(Files.exists(configFile()));

        registry.setEnabled("a", true);
        assertTrue(manager.isDirty());
        assertTrue(manager.save());

        assertTrue(Files.isRegularFile(configFile()));
        assertFalse(manager.isDirty());
    }

    @Test
    void moduleStateAndSettingsSurviveARoundTrip() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(FakeModule.withSettings("a"));
        registry.register(FakeModule.withSettings("b"));
        ConfigManager manager = newManager(registry);
        Setting<Double> hudScale = manager.addGlobalSetting(Settings.decimal("hudScale", 1.0, 0.5, 3.0));
        manager.load();

        registry.setEnabled("a", true);
        hudScale.set(1.75);
        QoLModule module = registry.byId("a").orElseThrow();
        module.setting("offsetX").orElseThrow().fromRaw(42);
        module.setting("anchor").orElseThrow().fromRaw("bottom_right");
        module.setting("macros").orElseThrow().fromRaw(List.of("gg", "wp"));
        module.setting("visible").orElseThrow().fromRaw(false);
        assertTrue(manager.save());

        ModuleRegistry restoredRegistry = new ModuleRegistry();
        restoredRegistry.register(FakeModule.withSettings("a"));
        restoredRegistry.register(FakeModule.withSettings("b"));
        ConfigManager restored = newManager(restoredRegistry);
        Setting<Double> restoredScale = restored.addGlobalSetting(Settings.decimal("hudScale", 1.0, 0.5, 3.0));

        assertTrue(restored.load());
        assertEquals(ConfigIO.Status.OK, restored.lastStatus());
        assertFalse(restored.isDirty(), "loading is not a change");
        assertTrue(restoredRegistry.isEnabled("a"));
        assertFalse(restoredRegistry.isEnabled("b"));
        assertEquals(1.75, restoredScale.get().doubleValue());

        QoLModule restoredModule = restoredRegistry.byId("a").orElseThrow();
        assertEquals(42, intValueOf(restoredModule, "offsetX"));
        assertEquals(FakeModule.Anchor.BOTTOM_RIGHT, valueOf(restoredModule, "anchor"));
        assertEquals(List.of("gg", "wp"), valueOf(restoredModule, "macros"));
        assertFalse(boolValueOf(restoredModule, "visible"));
    }

    @Test
    void globalSettingIdsMustBeUnique() {
        ConfigManager manager = newManager(new ModuleRegistry());
        manager.addGlobalSetting(Settings.bool("notifications", true));

        assertThrows(IllegalArgumentException.class,
                () -> manager.addGlobalSetting(Settings.bool("notifications", false)));
        assertEquals(1, manager.globalSettings().size());
        assertTrue(manager.globalSetting("notifications").isPresent());
        assertTrue(manager.globalSetting("absent").isEmpty());
    }

    @Test
    void aNegativeDebounceIsRejected() {
        ConfigManager manager = newManager(new ModuleRegistry());
        assertThrows(IllegalArgumentException.class, () -> manager.setAutoSaveMillis(-1));
        assertEquals(ConfigManager.DEFAULT_AUTOSAVE_MILLIS, manager.autoSaveMillis());
    }

    @Test
    void theWrittenDocumentCarriesItsIdentity() throws IOException {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(FakeModule.withSettings("a"));
        ConfigManager manager = newManager(registry);
        manager.load();
        registry.setEnabled("a", true);
        assertTrue(manager.save());

        JsonObject written = readConfig(configFile());
        assertEquals(ConfigManager.SCHEMA_VERSION, written.get("schemaVersion").getAsInt());
        assertEquals("wokewtf-lite", written.get("mod").getAsString());
        assertTrue(written.getAsJsonObject("modules").getAsJsonObject("a").get("enabled").getAsBoolean());
    }
}
