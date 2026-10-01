package wtf.woke.lite.modules.util;

import net.minecraft.client.MinecraftClient;
import wtf.woke.lite.core.KeybindAction;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;

/**
 * A key of your own for toggling fullscreen.
 *
 * <p>The vanilla binding for this is fixed to F11, which some window managers
 * and some laptop keyboards make awkward. This calls the window's own toggle,
 * the same one F11 calls, so the mode switch and the settings it touches are
 * the game's.</p>
 *
 * <p>Off by default and unbound by default: it is a shortcut for something the
 * player can already do, not a behaviour change, so it stays out of the way
 * until asked for.</p>
 */
public final class FullscreenKeyModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "util.fullscreen_key";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.util.fullscreen_key";

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
                KeybindAction.UNBOUND, this::toggle));
    }

    /** Toggles the window, the same way the vanilla binding does. */
    public void toggle() {
        if (!isEnabled()) {
            return;
        }
        MinecraftClient.getInstance().getWindow().toggleFullscreen();
    }
}
