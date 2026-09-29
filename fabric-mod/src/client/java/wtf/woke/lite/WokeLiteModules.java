package wtf.woke.lite;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.util.ScreenshotRecorder;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.modules.hud.FpsModule;
import wtf.woke.lite.modules.ui.BookModule;
import wtf.woke.lite.modules.ui.ChatModule;
import wtf.woke.lite.modules.ui.CrosshairModule;
import wtf.woke.lite.modules.ui.InventorySortModule;
import wtf.woke.lite.modules.ui.ScreenshotModule;
import wtf.woke.lite.modules.ui.WaypointModule;
import wtf.woke.lite.modules.util.AutoReconnectModule;
import wtf.woke.lite.modules.util.ChatMacrosModule;
import wtf.woke.lite.modules.util.FullscreenKeyModule;
import wtf.woke.lite.modules.util.RespawnConfirmModule;
import wtf.woke.lite.modules.util.ScreenshotKeyModule;
import wtf.woke.lite.modules.util.StatsModule;

/**
 * The module graph this build ships, in one place.
 *
 * <p>Separate from the entrypoint because the two jobs are different: this one
 * says what the mod is made of and in which order it is built, while the
 * entrypoint says how it is started. Registration order is the order they appear
 * in the config and the HUD, so it is worth being able to read at a glance.</p>
 *
 * <p>Modules the rest of the startup has to talk to come back in a
 * {@link Graph}; the rest are fire-and-forget, since the framework reaches them
 * through the registry.</p>
 */
final class WokeLiteModules {

    private WokeLiteModules() {
        throw new AssertionError("No instances of " + WokeLiteModules.class.getName());
    }

    /**
     * The modules the startup sequence needs a handle on.
     *
     * @param crosshair      replaces the vanilla crosshair
     * @param waypoints      holds the stored waypoints
     * @param inventorySort  replays a sort plan
     * @param autoReconnect  answers a dropped connection
     * @param respawnConfirm holds the respawn button back
     * @param chatMacros     holds the stored chat macros
     * @param stats          holds the local counters
     */
    record Graph(CrosshairModule crosshair, WaypointModule waypoints, InventorySortModule inventorySort,
            AutoReconnectModule autoReconnect, RespawnConfirmModule respawnConfirm, ChatMacrosModule chatMacros,
            StatsModule stats) {
    }

    /** Registers every module this build ships. */
    static Graph register(ModuleRegistry registry) {
        registry.register(new FpsModule());
        CrosshairModule crosshair = new CrosshairModule();
        registry.register(crosshair);
        registry.register(new ChatModule());
        registry.register(new BookModule());
        registry.register(new ScreenshotModule(screenshotDirectory()));
        WaypointModule waypoints = new WaypointModule();
        registry.register(waypoints);
        InventorySortModule inventorySort = new InventorySortModule();
        registry.register(inventorySort);

        AutoReconnectModule autoReconnect = new AutoReconnectModule();
        registry.register(autoReconnect);
        RespawnConfirmModule respawnConfirm = new RespawnConfirmModule();
        registry.register(respawnConfirm);
        ChatMacrosModule chatMacros = new ChatMacrosModule();
        registry.register(chatMacros);
        registry.register(new ScreenshotKeyModule());
        registry.register(new FullscreenKeyModule());
        StatsModule stats = new StatsModule();
        registry.register(stats);

        return new Graph(crosshair, waypoints, inventorySort, autoReconnect, respawnConfirm, chatMacros, stats);
    }

    /** @return the directory the game writes screenshots into */
    private static Path screenshotDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve(ScreenshotRecorder.SCREENSHOTS_DIRECTORY);
    }
}
