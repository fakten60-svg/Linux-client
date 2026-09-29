package wtf.woke.lite.mixin.client;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import wtf.woke.lite.WokeLiteClient;
import wtf.woke.lite.chat.ChatHistory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.modules.ui.ChatModule;

/**
 * Applies the chat module at the point where every chat line enters the HUD.
 *
 * <p><b>Why Fabric API is not enough:</b>
 * {@code ClientReceiveMessageEvents} only sees messages that arrive over the
 * network, so it would miss exactly the messages a chat prefix must cover
 * too — local command feedback and errors, and anything another client mod adds
 * through {@code ChatHud.addMessage}. The scrollback cap is worse still: it is
 * a private constant inside {@code ChatHud} with no API in front of it. Both
 * features therefore have to live where the hud itself is, and that is what
 * this mixin changes — two constants and one argument, nothing more.</p>
 *
 * <p>Every injection is a no-op unless the module is registered <em>and</em>
 * active, so with {@code ui.chat} off the hud behaves exactly like vanilla.</p>
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    /**
     * Prefixes the message with the current time before the hud stores it.
     *
     * <p>Targeting the three-argument overload means the one-argument
     * convenience overload is covered as well, without the timestamp being
     * applied twice.</p>
     */
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;"
                    + "Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true, index = 1)
    private Text wokewtf$prependTimestamp(Text message) {
        ChatModule chat = wokewtf$activeChatModule();
        return chat == null ? message : chat.timestamped(message);
    }

    /** Raises the cap on the scrollback the hud keeps. */
    @ModifyConstant(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
            constant = @Constant(intValue = ChatHistory.VANILLA_LENGTH))
    private int wokewtf$expandHistory(int vanillaLimit) {
        return wokewtf$historyLimit(vanillaLimit);
    }

    /** Raises the cap on the lines the hud keeps ready to draw. */
    @ModifyConstant(method = "addVisibleMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V",
            constant = @Constant(intValue = ChatHistory.VANILLA_LENGTH))
    private int wokewtf$expandVisibleHistory(int vanillaLimit) {
        return wokewtf$historyLimit(vanillaLimit);
    }

    private static int wokewtf$historyLimit(int vanillaLimit) {
        ChatModule chat = wokewtf$activeChatModule();
        return chat == null ? vanillaLimit : chat.effectiveHistoryLength();
    }

    /**
     * @return the chat module when it is registered and running, otherwise
     *         {@code null} — which every injection treats as "leave vanilla
     *         alone"
     */
    private static ChatModule wokewtf$activeChatModule() {
        ModuleRegistry registry = WokeLiteClient.registry();
        if (registry == null) {
            // No chat can exist before the client entrypoint has run, but the
            // hud classes load earlier than that, so this is reachable.
            return null;
        }
        QoLModule module = registry.byId(ChatModule.MODULE_ID).orElse(null);
        return module instanceof ChatModule chat && chat.isActive() ? chat : null;
    }
}
