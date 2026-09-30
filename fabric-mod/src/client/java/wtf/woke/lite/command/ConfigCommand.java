package wtf.woke.lite.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import wtf.woke.lite.screen.WokeConfigScreen;

/**
 * {@code /wokewtf config}.
 *
 * <p>Opens the settings screen — the same one the keybind opens, reached from
 * the chat box, which is the only way in for anyone who has not bound a key.
 * It says nothing in chat on purpose: the screen appearing <em>is</em> the
 * feedback, and a line of text about it would only scroll away.</p>
 *
 * <p>The screen remembers where it was opened from, so closing it returns to
 * whatever was on screen before rather than dumping the player into the world.</p>
 */
final class ConfigCommand {

    private ConfigCommand() {
        throw new AssertionError("No instances of " + ConfigCommand.class.getName());
    }

    /** @return the {@code config} subtree, ready to hang off the command root */
    static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommandManager.literal("config").executes(context -> open());
    }

    private static int open() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new WokeConfigScreen(client.currentScreen));
        return 1;
    }
}
