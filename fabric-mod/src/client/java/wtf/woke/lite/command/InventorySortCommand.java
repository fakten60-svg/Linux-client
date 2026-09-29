package wtf.woke.lite.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import wtf.woke.lite.modules.ui.InventorySortModule;
import wtf.woke.lite.inventory.SortResult;

/**
 * {@code /wokewtf sort}.
 *
 * <p>Asks the inventory sort module to start, and says why it did not when it
 * refuses. The module decides: it is the one that knows whether this client owns
 * the world it would be rearranging.</p>
 */
final class InventorySortCommand {

    private InventorySortCommand() {
        throw new AssertionError("No instances of " + InventorySortCommand.class.getName());
    }

    /** @return the {@code sort} subtree, ready to hang off the command root */
    static LiteralArgumentBuilder<FabricClientCommandSource> node(InventorySortModule module) {
        return ClientCommandManager.literal("sort")
                .executes(context -> sort(module, context));
    }

    private static int sort(InventorySortModule module, CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        SortResult result = module.startSort();
        // The count only appears in the outcomes whose message has room for it;
        // an unused argument is ignored by the translation.
        Text message = Text.translatable(result.translationKey(), module.pendingStacks());
        if (result.isSuccess()) {
            source.sendFeedback(message);
            return 1;
        }
        source.sendError(message);
        module.availability().reasonKey().ifPresent(reason -> source.sendError(Text.translatable(reason)));
        return 0;
    }
}
