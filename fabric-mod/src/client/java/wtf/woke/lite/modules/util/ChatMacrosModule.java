package wtf.woke.lite.modules.util;

import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import wtf.woke.lite.core.KeybindAction;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.macro.ChatMacro;
import wtf.woke.lite.macro.ChatMacroList;

/**
 * Puts a saved chat line in the chat box, behind a key of your own.
 *
 * <p>It <em>types</em>, it does not send. The key opens the ordinary chat screen
 * with the line already in it, and whether that line is ever sent is decided by
 * the player pressing Enter. That is also why nothing here can be a flood
 * filter bypass: the chat history, the send path and the rate limit are all the
 * game's own, and the mod contributes nothing to them.</p>
 *
 * <p>Off by default, like every other module that changes what the client does.
 * The bindings themselves are registered through the game's own keybind system,
 * so all {@value #SLOTS} slots appear in the Controls screen and are saved by
 * the game; each slot types whatever the macro in that position holds, which is
 * why removing a macro shifts the ones after it up.</p>
 */
public final class ChatMacrosModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "util.chat_macros";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.util.chat_macros";

    /** How many macro keybinds ship; one per storable macro. */
    public static final int SLOTS = ChatMacroList.MAX_ENTRIES;

    private final Setting<List<String>> macros;

    public ChatMacrosModule() {
        this.macros = Settings
                .stringList("macros", List.of(), ChatMacroList.MAX_ENTRIES, ChatMacroList.MAX_ENCODED_LENGTH)
                .describedBy(key("macros"), key("macros") + ".tooltip");
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String translationKey() {
        return TRANSLATION_KEY;
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.CONVENIENCE;
    }

    @Override
    public void onRegister(ModuleRegistry registry) {
        addSetting(macros);
        for (int slot = 1; slot <= SLOTS; slot++) {
            int bound = slot;
            registry.keybinds().register(new KeybindAction(slotKeybindId(slot), slotLabelKey(slot),
                    KeybindAction.UNBOUND, () -> type(bound)));
        }
    }

    /** @return the setting holding every stored macro */
    public Setting<List<String>> macros() {
        return macros;
    }

    /**
     * Opens the chat box with the macro of that slot in it.
     *
     * @param slot one-based slot number
     */
    public void type(int slot) {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) {
            // Only from the game itself: opening the chat box on top of a screen
            // the player is using would be worse than doing nothing.
            return;
        }
        Optional<ChatMacro> macro = ChatMacroList.inSlot(macros, slot);
        if (macro.isEmpty()) {
            report(Text.translatable(TRANSLATION_KEY + ".empty", slot));
            return;
        }
        client.setScreen(new ChatScreen(macro.get().text(), false));
    }

    /** @return the keybind id of a slot, so callers can name it */
    public static String slotKeybindId(int slot) {
        return MODULE_ID + ".slot" + slot;
    }

    private static String slotLabelKey(int slot) {
        return TRANSLATION_KEY + ".keybind.slot" + slot;
    }

    private static void report(Text message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
    }

    private static String key(String settingId) {
        return TRANSLATION_KEY + ".setting." + settingId;
    }
}
