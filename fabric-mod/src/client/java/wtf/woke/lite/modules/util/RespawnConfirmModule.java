package wtf.woke.lite.modules.util;

import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.screen.ConfirmDelay;

/**
 * Holds the respawn button back for a moment so a stray click cannot send you
 * back into the world.
 *
 * <p>It never respawns anybody. The only thing it does is switch the vanilla
 * button off for a few seconds and show how long is left; the button, its
 * action and the decision to press it stay exactly where they were. A mistake
 * in here can therefore only produce a button that is briefly unhelpful, which
 * is the whole point of keeping it that conservative.</p>
 *
 * <p>The counting lives in {@link ConfirmDelay} and is unit-tested without a
 * game. This class only finds the vanilla widget and talks to it, which is the
 * part that needs a client.</p>
 */
public final class RespawnConfirmModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "util.respawn_confirm";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.util.respawn_confirm";

    /** Vanilla translation key of the button this module holds back. */
    public static final String RESPAWN_BUTTON_KEY = "deathScreen.respawn";

    /** Bounds and default of the hold, in seconds. */
    public static final int MIN_SECONDS = 1;
    public static final int MAX_SECONDS = 10;
    public static final int DEFAULT_SECONDS = 3;

    private final Setting<Integer> delaySeconds;
    private final ConfirmDelay delay = new ConfirmDelay();

    private ButtonWidget guarded;
    private Screen guardedScreen;
    private Text originalLabel;

    public RespawnConfirmModule() {
        this.delaySeconds = Settings.integer("delaySeconds", DEFAULT_SECONDS, MIN_SECONDS, MAX_SECONDS)
                .describedBy(key("delaySeconds"), key("delaySeconds") + ".tooltip");
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
        addSetting(delaySeconds);
    }

    /**
     * Arms the hold when the death screen is the one that just opened.
     *
     * @param screen the screen that finished initialising
     */
    public void onScreenInitialised(Screen screen) {
        if (!isEnabled() || !(screen instanceof DeathScreen)) {
            return;
        }
        ButtonWidget button = findRespawnButton(screen);
        if (button == null || !button.active) {
            return;
        }
        // Hardcore deaths show "Spectate" instead, so nothing here matches and
        // the module simply does nothing — which is the right outcome.
        guarded = button;
        guardedScreen = screen;
        originalLabel = button.getMessage();
        button.active = false;
        delay.arm(delaySeconds.get());
        WokeLite.LOGGER.info("Holding the respawn button for {}s", delaySeconds.get());
    }

    @Override
    public void onClientTick() {
        if (guarded == null) {
            return;
        }
        if (MinecraftClient.getInstance().currentScreen != guardedScreen) {
            // The screen went away underneath us; stop touching its widget.
            clear();
            return;
        }
        if (delay.tick()) {
            release();
            return;
        }
        guarded.setMessage(Text.translatable(TRANSLATION_KEY + ".waiting", delay.remainingSeconds()));
    }

    /** Switching the module off restores the button straight away. */
    @Override
    public void onDisable() {
        release();
    }

    /** @return whether a button is being held back right now */
    public boolean isHolding() {
        return guarded != null;
    }

    private void release() {
        if (guarded != null) {
            guarded.setMessage(originalLabel);
            guarded.active = true;
        }
        clear();
    }

    private void clear() {
        guarded = null;
        guardedScreen = null;
        originalLabel = null;
        delay.clear();
    }

    private static ButtonWidget findRespawnButton(Screen screen) {
        List<ClickableWidget> widgets = Screens.getButtons(screen);
        for (ClickableWidget widget : widgets) {
            if (widget instanceof ButtonWidget button && isRespawnLabel(button.getMessage())) {
                return button;
            }
        }
        return null;
    }

    private static boolean isRespawnLabel(Text label) {
        if (label == null) {
            return false;
        }
        TextContent content = label.getContent();
        if (content instanceof TranslatableTextContent translatable) {
            return RESPAWN_BUTTON_KEY.equals(translatable.getKey());
        }
        // Fallback for a label the game already resolved: compare the text the
        // vanilla key would render, so a wrapper around it is still recognised.
        return label.getString().equals(Text.translatable(RESPAWN_BUTTON_KEY).getString());
    }

    private static String key(String settingId) {
        return TRANSLATION_KEY + ".setting." + settingId;
    }
}
