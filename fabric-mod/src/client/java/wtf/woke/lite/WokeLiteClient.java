package wtf.woke.lite;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
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
import wtf.woke.lite.input.KeybindBridge;
import wtf.woke.lite.modules.hud.FpsModule;
import wtf.woke.lite.modules.ui.InventorySortModule;
import wtf.woke.lite.modules.ui.WaypointModule;
import wtf.woke.lite.screen.WokeConfigScreen;

/**
 * Client entrypoint: builds the module graph, loads the config, and hands the
 * whole thing to Fabric's client events.
 *
 * <p>Startup order matters and is deliberate: modules register first so their
 * settings and keybinds exist, the keybinds are then handed to the game,
 * shipped defaults are applied third, and the config file is loaded last so it
 * can override both. Anything that later needs the framework reads it through
 * the accessors below. Which modules exist is {@link WokeLiteModules}' business,
 * not this class's.</p>
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
    private static KeybindBridge keybinds;
    private static WokeLiteModules.Graph graph;

    @Override
    public void onInitializeClient() {
        registry = new ModuleRegistry();
        config = new ConfigManager(registry, new ConfigIO(FabricLoader.getInstance().getConfigDir()));

        Setting<Boolean> background = config.addGlobalSetting(
                Settings.bool("hud.background", true).describedBy("wokewtf.lite.setting.hud.background", null));
        Setting<Integer> backgroundColor = config.addGlobalSetting(
                Settings.color("hud.backgroundColor", DEFAULT_BACKGROUND_COLOR)
                        .describedBy("wokewtf.lite.setting.hud.backgroundColor", null));
        Setting<Integer> textColor = config.addGlobalSetting(
                Settings.color("hud.textColor", DEFAULT_TEXT_COLOR)
                        .describedBy("wokewtf.lite.setting.hud.textColor", null));
        // Verbose logging for one module. Empty means "watch nothing", and a
        // typo behaves the same way: the point is to narrow the log down, not to
        // widen it by accident.
        Setting<String> debugModule = config.addGlobalSetting(
                Settings.text("debugModule", "", 32)
                        .describedBy("wokewtf.lite.setting.debugModule", "wokewtf.lite.setting.debugModule.tooltip"));

        graph = WokeLiteModules.register(registry);
        // The config screen belongs to the mod rather than to one module, so its
        // bind is declared here, after the modules and before the bindings are
        // handed to the game.
        registry.keybinds().register(WokeConfigScreen.keybind());
        keybinds = new KeybindBridge(registry.keybinds());
        int bindings = keybinds.install();
        applyShippedDefaults();

        boolean loaded = config.load();
        registry.freeze();

        dispatcher = new ModuleDispatcher(registry);
        dispatcher.debugWatch().watch(debugModule.get());
        debugModule.onChanged(dispatcher.debugWatch()::watch);
        HudRenderer hudRenderer = new HudRenderer(registry, background, backgroundColor, textColor);
        ClientEvents.register(config, dispatcher, hudRenderer, graph.crosshair(), graph.waypoints(),
                graph.inventorySort(), new ClientEvents.Convenience(keybinds, graph.autoReconnect(),
                        graph.respawnConfirm(), graph.chatMacros(), graph.stats()));

        WokeLite.LOGGER.info("{} v{} ready: {} modules ({} enabled), {} keybinds, config {} (Minecraft 1.21.11, "
                + "Fabric Loader {})", WokeLite.MOD_NAME, resolvedVersion(), registry.size(), enabledModuleCount(),
                bindings, loaded ? "loaded" : "defaults", loaderVersion());
        // Named one by one: the count above says a module went missing, this
        // says which, and it is the only line that proves a module registered
        // rather than merely being counted.
        WokeLite.LOGGER.info("Registered modules: {}",
                String.join(", ", registry.all().stream().map(QoLModule::id).toList()));
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

    /** The mod's keybind declarations, as the game knows them. */
    public static KeybindRegistry keybinds() {
        return registry.keybinds();
    }

    /** The module holding the player's stored waypoints. */
    public static WaypointModule waypoints() {
        return graph.waypoints();
    }

    /** The module that reorders the player's own inventory. */
    public static InventorySortModule inventorySort() {
        return graph.inventorySort();
    }

    /**
     * Applies the state a fresh install should start in. The config file, if it
     * exists, is loaded afterwards and wins.
     *
     * <p>Only {@code hud.fps} is enabled here. Everything else — the interface
     * modules and every convenience module — changes how the game behaves or
     * looks, so all of it stays opt-in. That includes the two the request did
     * not name a default for ({@code util.respawn_confirm}, which delays a
     * button, and {@code util.stats}, which starts writing counters): the rule
     * this build has followed so far is that nothing is switched on for the
     * player.</p>
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
