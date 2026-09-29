package wtf.woke.lite.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import wtf.woke.lite.modules.util.StatsModule;
import wtf.woke.lite.stats.StatSnapshot;

/**
 * {@code /wokewtf stats [reset]}.
 *
 * <p>Shows the local counters and, on request, throws them away. Says nothing
 * about anybody else's numbers and asks no server for anything: the values are
 * the ones this client wrote into its own config.</p>
 */
final class StatsCommand {

    /** Base key for everything this command says. */
    private static final String COMMAND_KEY = "wokewtf.lite.command.stats";

    private StatsCommand() {
        throw new AssertionError("No instances of " + StatsCommand.class.getName());
    }

    /** @return the {@code stats} subtree, ready to hang off the command root */
    static LiteralArgumentBuilder<FabricClientCommandSource> node(StatsModule module) {
        return ClientCommandManager.literal("stats")
                .executes(context -> show(module, context))
                .then(ClientCommandManager.literal("reset")
                        .executes(context -> reset(module, context)));
    }

    private static int show(StatsModule module, CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        StatSnapshot snapshot = module.snapshot();
        source.sendFeedback(Text.translatable(COMMAND_KEY + ".header"));
        source.sendFeedback(Text.translatable(COMMAND_KEY + ".blocks", snapshot.blocksMined()));
        source.sendFeedback(Text.translatable(COMMAND_KEY + ".distance", Math.round(snapshot.distanceWalked())));
        source.sendFeedback(Text.translatable(COMMAND_KEY + ".playtime", formatPlaytime(snapshot.playTimeSeconds())));
        return 1;
    }

    private static int reset(StatsModule module, CommandContext<FabricClientCommandSource> context) {
        module.resetCounters();
        context.getSource().sendFeedback(Text.translatable(COMMAND_KEY + ".reset"));
        return 1;
    }

    /** @return whole seconds as {@code 1h 02m 03s}, leaving out empty leading parts */
    static String formatPlaytime(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m " + remainder + "s";
        }
        if (minutes > 0L) {
            return minutes + "m " + remainder + "s";
        }
        return remainder + "s";
    }
}
