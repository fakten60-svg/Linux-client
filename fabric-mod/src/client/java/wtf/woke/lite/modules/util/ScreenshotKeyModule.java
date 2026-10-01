package wtf.woke.lite.modules.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import wtf.woke.lite.core.KeybindAction;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;

/**
 * A key of your own for taking a screenshot.
 *
 * <p>Useful when F2 is already taken, or when the hand is on the other side of
 * the keyboard. It calls the game's own screenshot method rather than
 * reimplementing one, so the file lands where every other screenshot lands and
 * the result is identical to pressing F2 — including travelling through the
 * same path the mod's screenshot helper already watches, so a screenshot taken
 * this way is offered on the clipboard just like any other.</p>
 *
 * <p>Off by default and unbound by default, so it cannot take a key away from
 * anything until the player asks for it.</p>
 */
public final class ScreenshotKeyModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "util.screenshot_key";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.util.screenshot_key";

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
        registry.keybinds().register(new KeybindAction(MODULE_ID, TRANSLATION_KEY + ".keybind",
                KeybindAction.UNBOUND, this::capture));
    }

    /** Takes a screenshot through the game's own recorder. */
    public void capture() {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(),
                message -> client.inGameHud.getChatHud().addMessage(message));
    }
}
