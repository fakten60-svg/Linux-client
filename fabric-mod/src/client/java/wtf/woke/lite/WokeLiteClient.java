package wtf.woke.lite;

import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.util.ScreenshotRecorder;
import wtf.woke.lite.config.ConfigIO;
import wtf.woke.lite.config.ConfigManager;
import wtf.woke.lite.core.KeybindRegistry;
import wtf.woke.lite.core.ModuleDispatcher;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.events.ClientEvents;
import wtf.woke.lite.hud.HudRenderer;
import wtf.woke.lite.modules.hud.FpsModule;
import wtf.woke.lite.modules.ui.BookModule;
import wtf.woke.lite.modules.ui.ChatModule;
import wtf.woke.lite.modules.ui.CrosshairModule;
import wtf.woke.lite.modules.ui.InventorySortModule;
import wtf.woke.lite.modules.ui.ScreenshotModule;
import wtf.woke.lite.modules.ui.WaypointModule;

/**
 * Client entrypoint: builds the module graph, loads the config, and hands the
 * whole thing to Fabric's client events.
 *
 * <p>Startup order matters and is deliberate: modules register first so their
 * settings exist, shipped defaults are applied second, and the config file is
 * loaded last so it can override both. Anything that later needs the framework
 * (config screen, keybinds) reads it through the accessors below.</p>
 */
@Environment(EnvType.CLIENT)
public final class WokeLiteClient implements ClientModInitializer {

    /** Default backing plate for HUD elements: dark, mostly opaque. */
    public static final int DEFAULT_BACKGROUND_COLOR = 0x8010141C;

    /** Default HUD text colour: soft off-white. */
    public static final int DEFAULT_TEXT_COLOR = 0xFFE8EAED;

    private static ModuleRegistry registry;
    private static ConfigManager config;
    private static ModuleDispatcher dispatcher;
    private static KeybindRegistry keybinds;
    private static CrosshairModule crosshair;
    private static WaypointModule waypoints;
    private static InventorySortModule inventorySort;

    @Override
    public void onInitializeClient() {
        registry = new ModuleRegistry();
        keybinds = new KeybindRegistry();
        config = new ConfigManager(registry, new ConfigIO(configDirectory()));

        Setting<Boolean> background = config.addGlobalSetting(
                Settings.bool("hud.background", true).describedBy("wokewtf.lite.setting.hud.background", null));
        Setting<Integer> backgroundColor = config.addGlobalSetting(
                Settings.color("hud.backgroundColor", DEFAULT_BACKGROUND_COLOR)
                        .describedBy("wokewtf.lite.setting.hud.backgroundColor", null));
        Setting<Integer> textColor = config.addGlobalSetting(
                Settings.color("hud.textColor", DEFAULT_TEXT_COLOR)
                        .describedBy("wokewtf.lite.setting.hud.textColor", null));

        registerModules();
        applyShippedDefaults();

        boolean loaded = config.load();
        registry.freeze();

        dispatcher = new ModuleDispatcher(registry);
        HudRenderer hudRenderer = new HudRenderer(registry, background, backgroundColor, textColor);
        ClientEvents.register(config, dispatcher, hudRenderer, crosshair, waypoints, inventorySort);

        WokeLite.LOGGER.info("{} v{} ready: {} modules ({} enabled), config {} (Minecraft 1.21.11, Fabric Loader {})",
                WokeLite.MOD_NAME, resolvedVersion(), registry.size(), enabledModuleCount(),
                loaded ? "loaded" : "defaults", loaderVersion());
    }

    /** The module registry built during initialisation. */
    public static ModuleRegistry registry() {
        return registry;
    }

    /** The config manager built during initialisation. */
    public static ConfigManager config() {
        return config;
    }

    /** The module dispatcher built during initialisation. */
    public static ModuleDispatcher dispatcher() {
        return dispatcher;
    }

    /** The keybind registry, filled in as keybinding features land. */
    public static KeybindRegistry keybinds() {
        return keybinds;
    }

    /** The module holding the player's stored waypoints. */
    public static WaypointModule waypoints() {
        return waypoints;
    }

    /** The module that reorders the player's own inventory. */
    public static InventorySortModule inventorySort() {
        return inventorySort;
    }

    private static Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /** Registers every module this build ships. */
    private static void registerModules() {
        registry.register(new FpsModule());
        crosshair = new CrosshairModule();
        registry.register(crosshair);
        registry.register(new ChatModule());
        registry.register(new BookModule());
        registry.register(new ScreenshotModule(screenshotDirectory()));
        waypoints = new WaypointModule();
        registry.register(waypoints);
        inventorySort = new InventorySortModule();
        registry.register(inventorySort);
    }

    /** @return the directory the game writes screenshots into */
    private static Path screenshotDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(ScreenshotRecorder.SCREENSHOTS_DIRECTORY);
    }

    /**
     * Applies the state a fresh install should start in. The config file, if it
     * exists, is loaded afterwards and wins.
     *
     * <p>The interface modules ({@code ui.crosshair}, {@code ui.chat},
     * {@code ui.book}, {@code ui.screenshot}, {@code ui.waypoints},
     * {@code ui.inventory_sort}) are deliberately not enabled here: they change
     * how the game behaves or looks, so they stay opt-in.</p>
     */
    private static void applyShippedDefaults() {
        registry.setEnabled(FpsModule.MODULE_ID, true);
    }

    private static long enabledModuleCount() {
        return registry.all().stream().filter(QoLModule::isEnabled).count();
    }

    private static String resolvedVersion() {
        return FabricLoader.getInstance()
                .getModContainer(WokeLite.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String loaderVersion() {
        return FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
