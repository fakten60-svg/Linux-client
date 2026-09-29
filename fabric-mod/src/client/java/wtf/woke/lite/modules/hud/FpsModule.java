package wtf.woke.lite.modules.hud;

import net.minecraft.client.MinecraftClient;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.hud.HudAnchor;
import wtf.woke.lite.hud.HudBounds;
import wtf.woke.lite.hud.HudContext;
import wtf.woke.lite.hud.HudElement;

/**
 * Frame-rate readout.
 *
 * <p>Read-only: it reports what the client is already measuring and changes
 * nothing about rendering or gameplay.</p>
 */
public final class FpsModule extends HudElement {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "hud.fps";

    private final Setting<Boolean> showLabel;
    private boolean loggedFirstFrame;

    public FpsModule() {
        this.showLabel = Settings.bool("showLabel", true).describedBy(settingKey("showLabel"), null);
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String translationKey() {
        return "wokewtf.lite.module.hud.fps";
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.HUD;
    }

    @Override
    public void onRegister(ModuleRegistry registry) {
        addLayoutSettings(HudAnchor.TOP_LEFT, 4, 4);
        addSetting(showLabel);
    }

    @Override
    protected int contentWidth(HudContext context) {
        return context.textWidth(text());
    }

    @Override
    protected int contentHeight(HudContext context) {
        return context.lineHeight();
    }

    @Override
    protected void draw(HudContext context, HudBounds screenBounds) {
        String text = text();
        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            WokeLite.LOGGER.info("{} first frame: '{}' at ({}, {})", id(), text, screenBounds.x(), screenBounds.y());
        }
        context.drawContext().drawTextWithShadow(context.textRenderer(), text,
                screenBounds.x(), screenBounds.y(), context.textColor());
    }

    private String text() {
        int fps = MinecraftClient.getInstance().getCurrentFps();
        return showLabel.get() ? "FPS: " + fps : Integer.toString(fps);
    }
}
