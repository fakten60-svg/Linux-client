package wtf.woke.lite.config;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;

/**
 * Owns the lifetime of the config: reading it at startup, applying it to the
 * module registry, and writing it back once changes have settled.
 *
 * <p>Unknown data is preserved by construction: a save starts from a deep copy
 * of the last file that loaded successfully, so keys written by another build
 * survive a round-trip. A file whose schema version is newer than this build
 * understands is read-only rather than downgraded. Writes are debounced through
 * {@link #tick()}; the document itself is shaped by {@link ConfigJson}.</p>
 */
public final class ConfigManager {

    /** Schema version this build writes. */
    public static final int SCHEMA_VERSION = 1;

    /** Default debounce between a change and the file write. */
    public static final long DEFAULT_AUTOSAVE_MILLIS = 5_000L;

    private final ModuleRegistry registry;
    private final ConfigIO io;
    private final LongSupplier clock;
    private final List<Setting<?>> globalSettings = new ArrayList<>(8);

    private JsonObject loadedRoot;
    private long autoSaveMillis = DEFAULT_AUTOSAVE_MILLIS;
    private long dirtySince = -1L;
    private boolean dirty;
    private boolean readOnly;
    private ConfigIO.Status lastStatus = ConfigIO.Status.MISSING;

    public ConfigManager(ModuleRegistry registry, ConfigIO io) {
        this(registry, io, System::currentTimeMillis);
    }

    /** @param clock injected millisecond clock, so the debounce is testable without sleeping */
    public ConfigManager(ModuleRegistry registry, ConfigIO io, LongSupplier clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.io = Objects.requireNonNull(io, "io");
        this.clock = Objects.requireNonNull(clock, "clock");
        registry.setDirtyMarker(this::markDirty);
    }

    // --- global settings ------------------------------------------------------

    /** Declares a setting owned by the mod itself rather than by a module. */
    public <T> Setting<T> addGlobalSetting(Setting<T> setting) {
        Objects.requireNonNull(setting, "setting");
        if (globalSetting(setting.id()).isPresent()) {
            throw new IllegalArgumentException("duplicate global setting id '" + setting.id() + "'");
        }
        setting.addListener(this::markDirty);
        globalSettings.add(setting);
        return setting;
    }

    public List<Setting<?>> globalSettings() {
        return List.copyOf(globalSettings);
    }

    public Optional<Setting<?>> globalSetting(String id) {
        return globalSettings.stream().filter(setting -> setting.id().equals(id)).findFirst();
    }

    // --- load / save ----------------------------------------------------------

    /** @return {@code true} when an existing config file was applied */
    public boolean load() {
        ConfigIO.ReadResult result = io.read();
        lastStatus = result.status();
        loadedRoot = null;
        readOnly = false;

        if (result.status() != ConfigIO.Status.OK) {
            clearDirty();
            return false;
        }

        JsonObject root = result.root();
        int version = ConfigJson.readInt(root, ConfigJson.KEY_SCHEMA, SCHEMA_VERSION);
        if (version > SCHEMA_VERSION) {
            readOnly = true;
            WokeLite.LOGGER.warn("Config schema v{} is newer than this build writes (v{}); leaving {} untouched",
                    version, SCHEMA_VERSION, io.path());
            clearDirty();
            return false;
        }

        loadedRoot = root;
        ConfigJson.applyGlobal(root, globalSettings);
        int modules = applyModules(root);
        clearDirty();
        WokeLite.LOGGER.info("Loaded config from {} (schema v{}, {} module entries)", io.path(), version, modules);
        return true;
    }

    /** @return {@code true} when the file was written */
    public boolean save() {
        if (readOnly) {
            WokeLite.LOGGER.warn("Refusing to overwrite the newer config file at {}", io.path());
            return false;
        }
        JsonObject root = snapshot();
        if (!io.write(root)) {
            return false;
        }
        loadedRoot = root;
        clearDirty();
        return true;
    }

    /** Debounced autosave. @return {@code true} when this call wrote the file */
    public boolean tick() {
        if (!dirty || readOnly) {
            return false;
        }
        if (autoSaveMillis > 0 && clock.getAsLong() - dirtySince < autoSaveMillis) {
            return false;
        }
        return save();
    }

    /** Records that in-memory state no longer matches the file. */
    public void markDirty() {
        if (!dirty) {
            dirty = true;
            dirtySince = clock.getAsLong();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    /** @return {@code true} when the loaded file was too new to be rewritten */
    public boolean isReadOnly() {
        return readOnly;
    }

    public long autoSaveMillis() {
        return autoSaveMillis;
    }

    /** @throws IllegalArgumentException if {@code millis} is negative */
    public void setAutoSaveMillis(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("autoSaveMillis must be >= 0");
        }
        this.autoSaveMillis = millis;
    }

    /** @return the outcome of the last {@link #load()} */
    public ConfigIO.Status lastStatus() {
        return lastStatus;
    }

    /** @return the exact document {@link #save()} would write */
    public JsonObject snapshot() {
        return ConfigJson.build(loadedRoot, SCHEMA_VERSION, WokeLite.MOD_ID, globalSettings, registry.all());
    }

    // --- internals ------------------------------------------------------------

    private int applyModules(JsonObject root) {
        int applied = 0;
        for (QoLModule module : registry.all()) {
            JsonObject entry = ConfigJson.moduleEntry(root, module.id());
            if (entry == null) {
                continue;
            }
            Boolean enabled = ConfigJson.enabledFlag(entry);
            if (enabled != null) {
                registry.setEnabled(module.id(), enabled);
                applied++;
            }
            ConfigJson.applySettings(entry, module.settings());
        }
        return applied;
    }

    private void clearDirty() {
        dirty = false;
        dirtySince = -1L;
    }
}
