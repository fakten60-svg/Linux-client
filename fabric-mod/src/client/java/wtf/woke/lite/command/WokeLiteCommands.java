package wtf.woke.lite.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.modules.ui.InventorySortModule;
import wtf.woke.lite.modules.ui.WaypointModule;
import wtf.woke.lite.modules.util.ChatMacrosModule;
import wtf.woke.lite.modules.util.StatsModule;

/**
 * The mod's client command, {@code /wokewtf}.
 *
 * <p>Registered through Fabric's <em>client</em> command API, so it is parsed
 * and executed inside this client and never travels to a server: typing it on
 * somebody else's server cannot be seen by that server, let alone refused by
 * it. It is the trigger for the features that have no keybind yet.</p>
 *
 * <p>Every subtree hangs off one root literal, because a second registration of
 * the same root would replace the first.</p>
 */
public final class WokeLiteCommands {

    /** Root of the command, e.g. {@code /wokewtf waypoint add home}. */
    public static final String ROOT = "wokewtf";

    private WokeLiteCommands() {
        throw new AssertionError("No instances of " + WokeLiteCommands.class.getName());
    }

    /**
     * Subscribes the command to Fabric's client command registry.
     *
     * @param waypoints     the module holding the stored waypoints
     * @param inventorySort the module that replays a sort plan
     * @param chatMacros    the module holding the stored chat macros
     * @param stats         the module holding the local counters
     */
    public static void register(WaypointModule waypoints, InventorySortModule inventorySort,
            ChatMacrosModule chatMacros, StatsModule stats) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal(ROOT)
                    .then(WaypointCommand.node(waypoints))
                    .then(InventorySortCommand.node(inventorySort))
                    .then(ChatMacroCommand.node(chatMacros))
                    .then(StatsCommand.node(stats)));
            // Runs on every world join: Fabric builds the client dispatcher then.
            WokeLite.LOGGER.info("Client command '/{}' ready for this session (waypoint, sort, macro, stats)", ROOT);
        });
    }
}
