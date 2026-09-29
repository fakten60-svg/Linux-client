package wtf.woke.lite.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import wtf.woke.lite.modules.ui.WaypointModule;
import wtf.woke.lite.waypoint.Waypoint;
import wtf.woke.lite.waypoint.WaypointResult;
import wtf.woke.lite.waypoint.Waypoints;

/**
 * {@code /wokewtf waypoint add|remove|list}.
 *
 * <p>Reads and writes the waypoint list of the module, which lives in this
 * client's config; nothing here talks to a server. Names are single words, which
 * is what the stored form and its separator allow.</p>
 */
final class WaypointCommand {

    /** Base key for everything this command says. */
    private static final String COMMAND_KEY = "wokewtf.lite.command.waypoint";

    /** Name of the argument holding a waypoint name. */
    private static final String NAME_ARGUMENT = "name";

    private WaypointCommand() {
        throw new AssertionError("No instances of " + WaypointCommand.class.getName());
    }

    /** @return the {@code waypoint} subtree, ready to hang off the command root */
    static LiteralArgumentBuilder<FabricClientCommandSource> node(WaypointModule module) {
        return ClientCommandManager.literal("waypoint")
                .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument(NAME_ARGUMENT, StringArgumentType.word())
                                .executes(context -> edit(module, context, true))))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument(NAME_ARGUMENT, StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(module.namesHere(), builder))
                                .executes(context -> edit(module, context, false))))
                .then(ClientCommandManager.literal("list")
                        .executes(context -> list(module, context)));
    }

    private static int edit(WaypointModule module, CommandContext<FabricClientCommandSource> context, boolean adding) {
        String name = StringArgumentType.getString(context, NAME_ARGUMENT);
        WaypointResult result = adding ? module.addHere(name) : module.removeHere(name);
        FabricClientCommandSource source = context.getSource();
        Text message = Text.translatable(result.translationKey(), name);
        if (result.isSuccess()) {
            source.sendFeedback(message);
            return 1;
        }
        source.sendError(message);
        return 0;
    }

    private static int list(WaypointModule module, CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        List<Waypoint> here = module.waypointsHere();
        if (here.isEmpty()) {
            source.sendFeedback(Text.translatable(COMMAND_KEY + ".list.empty"));
            return 0;
        }
        Vec3d position = source.getPosition();
        source.sendFeedback(Text.translatable(COMMAND_KEY + ".list.header", here.size()));
        for (Waypoint waypoint : Waypoints.nearestFirst(here, position.x, position.y, position.z)) {
            source.sendFeedback(Text.translatable(COMMAND_KEY + ".list.entry", waypoint.name(),
                    waypoint.distanceFrom(position.x, position.y, position.z),
                    waypoint.x(), waypoint.y(), waypoint.z()));
        }
        return here.size();
    }
}
