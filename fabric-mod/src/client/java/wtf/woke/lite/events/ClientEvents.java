package wtf.woke.lite.events;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.util.Identifier;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.command.WokeLiteCommands;
import wtf.woke.lite.config.ConfigManager;
import wtf.woke.lite.core.ModuleDispatcher;
import wtf.woke.lite.hud.CrosshairOverlay;
import wtf.woke.lite.hud.HudRenderer;
import wtf.woke.lite.modules.ui.CrosshairModule;
import wtf.woke.lite.modules.ui.InventorySortModule;
import wtf.woke.lite.modules.ui.WaypointModule;

/**
 * Every Fabric callback this mod subscribes to, in one auditable place.
 *
 * <p>Uses {@code HudElementRegistry} rather than the deprecated
 * {@code HudRenderCallback}, and attaches a single layer that renders all of
 * this mod's HUD elements, so new readouts never touch this file. Elements that
 * must take over a vanilla one register here too, which keeps the list of hooks
 * short enough to review.</p>
 */
public final class ClientEvents {

    /** Identifier of the single HUD layer this mod draws into. */
    private static final Identifier HUD_LAYER = Identifier.of(WokeLite.MOD_ID, "overlay");

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
     */
    public static void register(ConfigManager config, ModuleDispatcher dispatcher, HudRenderer hudRenderer,
            CrosshairModule crosshair, WaypointModule waypoints, InventorySortModule inventorySort) {
        HudElementRegistry.addLast(HUD_LAYER, (context, tickCounter) -> hudRenderer.render(context));

        // Client commands are read and answered locally; see WokeLiteCommands.
        WokeLiteCommands.register(waypoints, inventorySort);

        // The crosshair is an override, not an addition: the vanilla element is
        // handed to the overlay, which replays it whenever the module is off.
        CrosshairOverlay crosshairOverlay = new CrosshairOverlay(crosshair);
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, crosshairOverlay::replacing);

        // Client ticks are the heartbeat: modules get their tick, then the
        // debounced autosave gets its chance to flush.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            dispatcher.tickAll();
            config.tick();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> dispatcher.onJoinWorld());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            dispatcher.onLeaveWorld();
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
