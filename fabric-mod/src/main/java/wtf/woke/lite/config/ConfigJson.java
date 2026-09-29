package wtf.woke.lite.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;

/**
 * Translates between the module graph and the config document.
 *
 * <p>Package-private and stateless: {@link ConfigManager} owns when a save
 * happens, this class owns what the bytes look like. Keeping it separate means
 * the schema rules live in one readable place.</p>
 */
final class ConfigJson {

    static final String KEY_SCHEMA = "schemaVersion";
    static final String KEY_MOD = "mod";
    static final String KEY_GLOBAL = "global";
    static final String KEY_MODULES = "modules";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_SETTINGS = "settings";

    private ConfigJson() {
        throw new AssertionError("No instances of " + ConfigJson.class.getName());
    }

    /**
     * Builds the document to write, starting from {@code base} so that every
     * key this build does not know about is carried over untouched.
     */
    static JsonObject build(JsonObject base, int schemaVersion, String modId,
            List<Setting<?>> globalSettings, List<QoLModule> modules) {
        JsonObject root = base == null ? new JsonObject() : base.deepCopy();
        root.addProperty(KEY_SCHEMA, schemaVersion);
        root.addProperty(KEY_MOD, modId);

        JsonObject global = asObject(root.get(KEY_GLOBAL));
        writeSettings(global, globalSettings);
        root.add(KEY_GLOBAL, global);

        JsonObject entries = asObject(root.get(KEY_MODULES));
        for (QoLModule module : modules) {
            JsonObject entry = asObject(entries.get(module.id()));
            entry.addProperty(KEY_ENABLED, module.isEnabled());
            JsonObject values = asObject(entry.get(KEY_SETTINGS));
            writeSettings(values, module.settings());
            entry.add(KEY_SETTINGS, values);
            entries.add(module.id(), entry);
        }
        root.add(KEY_MODULES, entries);
        return root;
    }

    /** Applies the {@code global} block to the mod's own settings. */
    static void applyGlobal(JsonObject root, List<Setting<?>> globalSettings) {
        applyValues(asObject(root.get(KEY_GLOBAL)), globalSettings);
    }

    /**
     * @return the module's entry, or {@code null} when the file has none
     *         (a first run, or a module added since the file was written)
     */
    static JsonObject moduleEntry(JsonObject root, String moduleId) {
        JsonElement element = asObject(root.get(KEY_MODULES)).get(moduleId);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /** @return the stored enabled flag, or {@code null} when absent or not a boolean */
    static Boolean enabledFlag(JsonObject moduleEntry) {
        JsonElement enabled = moduleEntry.get(KEY_ENABLED);
        if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
            return null;
        }
        return enabled.getAsBoolean();
    }

    /** Applies the {@code settings} block of a module entry. */
    static void applySettings(JsonObject moduleEntry, List<Setting<?>> settings) {
        applyValues(asObject(moduleEntry.get(KEY_SETTINGS)), settings);
    }

    /** @return the stored integer, or {@code fallback} when absent or not a number */
    static int readInt(JsonObject root, String key, int fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return element.getAsInt();
    }

    private static void applyValues(JsonObject values, List<Setting<?>> settings) {
        for (Setting<?> setting : settings) {
            JsonElement value = values.get(setting.id());
            if (value != null) {
                setting.fromRaw(toRaw(value));
            }
        }
    }

    private static void writeSettings(JsonObject target, List<Setting<?>> settings) {
        for (Setting<?> setting : settings) {
            target.add(setting.id(), toJson(setting.toRaw()));
        }
    }

    private static JsonElement toJson(Object raw) {
        if (raw == null) {
            return JsonNull.INSTANCE;
        }
        if (raw instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (raw instanceof Number number) {
            return new JsonPrimitive(number);
        }
        if (raw instanceof List<?> list) {
            JsonArray array = new JsonArray();
            list.forEach(entry -> array.add(String.valueOf(entry)));
            return array;
        }
        return new JsonPrimitive(String.valueOf(raw));
    }

    private static Object toRaw(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonArray()) {
            List<String> values = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonPrimitive()) {
                    values.add(child.getAsString());
                }
            }
            return values;
        }
        if (!element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsNumber();
        }
        return primitive.isString() ? primitive.getAsString() : null;
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }
}
