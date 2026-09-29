package wtf.woke.lite.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import wtf.woke.lite.macro.ChatMacro;
import wtf.woke.lite.macro.ChatMacroList;
import wtf.woke.lite.modules.util.ChatMacrosModule;

/**
 * {@code /wokewtf macro set|remove|list}.
 *
 * <p>Edits the macro list of the module, which lives in this client's config;
 * nothing here talks to a server. This is how a macro gets its text — the
 * keybinds only decide which key types which slot — and it is a client command,
 * so a macro can never be authored by anyone but the player.</p>
 */
final class ChatMacroCommand {

    /** Base key for everything this command says. */
    private static final String COMMAND_KEY = "wokewtf.lite.command.macro";

    private static final String NAME_ARGUMENT = "name";
    private static final String TEXT_ARGUMENT = "text";

    private ChatMacroCommand() {
        throw new AssertionError("No instances of " + ChatMacroCommand.class.getName());
    }

    /** @return the {@code macro} subtree, ready to hang off the command root */
    static LiteralArgumentBuilder<FabricClientCommandSource> node(ChatMacrosModule module) {
        return ClientCommandManager.literal("macro")
                .then(ClientCommandManager.literal("set")
                        .then(ClientCommandManager.argument(NAME_ARGUMENT, StringArgumentType.word())
                                .then(ClientCommandManager.argument(TEXT_ARGUMENT, StringArgumentType.greedyString())
                                        .executes(context -> store(module, context)))))
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument(NAME_ARGUMENT, StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(names(module), builder))
                                .executes(context -> remove(module, context))))
                .then(ClientCommandManager.literal("list")
                        .executes(context -> list(module, context)));
    }

    private static int store(ChatMacrosModule module, CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, NAME_ARGUMENT);
        String text = StringArgumentType.getString(context, TEXT_ARGUMENT);
        return report(context, ChatMacroList.store(module.macros(), name, text), name);
    }

    private static int remove(ChatMacrosModule module, CommandContext<FabricClientCommandSource> context) {
        String name = StringArgumentType.getString(context, NAME_ARGUMENT);
        return report(context, ChatMacroList.remove(module.macros(), name), name);
    }

    private static int report(CommandContext<FabricClientCommandSource> context, ChatMacroList.Outcome outcome,
            String name) {
        FabricClientCommandSource source = context.getSource();
        Text message = Text.translatable(COMMAND_KEY + "." + outcome.name().toLowerCase(Locale.ROOT), name);
        if (outcome == ChatMacroList.Outcome.STORED || outcome == ChatMacroList.Outcome.REPLACED
                || outcome == ChatMacroList.Outcome.REMOVED) {
            source.sendFeedback(message);
            return 1;
        }
        source.sendError(message);
        return 0;
    }

    private static int list(ChatMacrosModule module, CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        List<ChatMacro> macros = ChatMacroList.all(module.macros());
        if (macros.isEmpty()) {
            source.sendFeedback(Text.translatable(COMMAND_KEY + ".list.empty"));
            return 0;
        }
        source.sendFeedback(Text.translatable(COMMAND_KEY + ".list.header", macros.size()));
        for (int index = 0; index < macros.size(); index++) {
            source.sendFeedback(Text.translatable(COMMAND_KEY + ".list.entry", index + 1, macros.get(index).name()));
        }
        return macros.size();
    }

    private static List<String> names(ChatMacrosModule module) {
        return ChatMacroList.all(module.macros()).stream().map(ChatMacro::name).toList();
    }
}
