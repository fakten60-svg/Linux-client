package wtf.woke.lite.events;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.command.WokeLiteCommands;
import wtf.woke.lite.config.ConfigManager;
import wtf.woke.lite.core.ModuleDispatcher;
import wtf.woke.lite.hud.CrosshairOverlay;
import wtf.woke.lite.hud.HudRenderer;
import wtf.woke.lite.input.KeybindBridge;
import wtf.woke.lite.input.KeybindConflictReport;
import wtf.woke.lite.modules.ui.CrosshairModule;
import wtf.woke.lite.modules.ui.InventorySortModule;
import wtf.woke.lite.modules.ui.WaypointModule;
import wtf.woke.lite.modules.util.AutoReconnectModule;
import wtf.woke.lite.modules.util.ChatMacrosModule;
import wtf.woke.lite.modules.util.RespawnConfirmModule;
import wtf.woke.lite.modules.util.StatsModule;
import wtf.woke.lite.session.JoinWelcome;

/**
 * Every Fabric callback this mod subscribes to, in one auditable place.
 *
 * <p>Uses {@code HudElementRegistry} rather than the deprecated
 * {@code HudRenderCallback}, and attaches a single layer that renders all of
 * this mod's HUD elements, so new readouts never touch this file. Elements that
 * must take over a vanilla one register here too, which keeps the list of hooks
 * short enough to review.</p>
 *
 * <p>The convenience modules subscribe here as well, for the same reason: what
 * the mod hooks into the game should be readable in one file, even when the
 * behaviour behind a hook lives somewhere else.</p>
 */
public final class ClientEvents {

    /** Identifier of the single HUD layer this mod draws into. */
    private static final Identifier HUD_LAYER = Identifier.of(WokeLite.MOD_ID, "overlay");

    /**
     * Chat line shown once per client run, after the first world join.
     *
     * <p>It names both ways into the settings screen, because a player who has
     * not found the keybind yet has also not found the command, and the screen
     * appearing is the only thing that would tell them either exists.</p>
     *
     * <p>Its English text lives in {@code en_us.json} — the default every other
     * language falls back to per key — and the German one in {@code de_de.json}.</p>
     */
    static final String CHAT_GUIDE_KEY = "wokewtf.lite.first_join.chat_guide";

    /**
     * The convenience-module wiring.
     *
     * <p>Grouped into one value rather than five more parameters: the hooks
     * below are the only reason these exist together, and a positional list that
     * long is a mistake waiting to happen.</p>
     *
     * @param keybinds      the bridge polling the mod's bindings
     * @param autoReconnect the module answering a dropped connection
     * @param respawnConfirm the module holding the respawn button back
     * @param chatMacros    the module behind the macro command
     * @param stats         the module behind the stats command
     */
    public record Convenience(KeybindBridge keybinds, AutoReconnectModule autoReconnect,
            RespawnConfirmModule respawnConfirm, ChatMacrosModule chatMacros, StatsModule stats) {
    }

    private ClientEvents() {
        throw new AssertionError("No instances of " + ClientEvents.class.getName());
    }

    /**
     * Wires the framework to the client.
     *
     * @param config        the config manager whose autosave rides the tick event
     * @param dispatcher    the module dispatcher driving per-tick module work
     * @param hudRenderer   the renderer behind the mod's HUD layer
     * @param crosshair     the module whose crosshair replaces the vanilla one
     * @param waypoints     the module the {@code waypoint} client command edits
     * @param inventorySort the module the {@code sort} client command starts
     * @param convenience   the convenience modules and the keybind bridge
     */
    public static void register(ConfigManager config, ModuleDispatcher dispatcher, HudRenderer hudRenderer,
            CrosshairModule crosshair, WaypointModule waypoints, InventorySortModule inventorySort,
            Convenience convenience) {
        HudElementRegistry.addLast(HUD_LAYER, (context, tickCounter) -> hudRenderer.render(context));

        // Client commands are read and answered locally; see WokeLiteCommands.
        WokeLiteCommands.register(waypoints, inventorySort, convenience.chatMacros(), convenience.stats());

        // The crosshair is an override, not an addition: the vanilla element is
        // handed to the overlay, which replays it whenever the module is off.
        CrosshairOverlay crosshairOverlay = new CrosshairOverlay(crosshair);
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, crosshairOverlay::replacing);

        // The death screen is the only screen this mod touches, and only to make
        // one of its own buttons wait; see RespawnConfirmModule.
        ScreenEvents.AFTER_INIT.register(
                (client, screen, width, height) -> convenience.respawnConfirm().onScreenInitialised(screen));

        // The startup conflict check cannot run at mod init: the game has not
        // built its options yet, so the bindings do not exist. The first tick is
        // the earliest moment they do.
        KeybindConflictReport conflictReport = new KeybindConflictReport();

        // The join guide is armed by the join and printed by the tick after it,
        // once per client run; see JoinWelcome for why it is not a bare flag.
        JoinWelcome welcome = new JoinWelcome();

        // Client ticks are the heartbeat: modules get their tick, then the
        // mod's own bindings are polled, then the debounced autosave flushes.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            dispatcher.tickAll();
            convenience.keybinds().poll();
            conflictReport.reportOnce(convenience.keybinds());
            if (welcome.consume()) {
                client.inGameHud.getChatHud().addMessage(Text.translatable(CHAT_GUIDE_KEY));
            }
            config.tick();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            dispatcher.onJoinWorld();
            convenience.autoReconnect().onJoined(client);
            welcome.arm();
            // Bindings can be changed between sessions, so the check is repeated
            // every time a world is entered rather than only at startup.
            conflictReport.report(convenience.keybinds());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            dispatcher.onLeaveWorld();
            convenience.autoReconnect().onDisconnected(client);
            saveIfDirty(config);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveIfDirty(config));
    }

    private static void saveIfDirty(ConfigManager config) {
        if (config.isDirty()) {
            config.save();
        }
    }
}
