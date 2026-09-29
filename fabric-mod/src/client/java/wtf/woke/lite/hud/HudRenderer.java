package wtf.woke.lite.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.ModuleDispatcher;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;

/**
 * Draws every active {@link HudElement} attached to the registry.
 *
 * <p>One Fabric HUD layer is registered for the whole mod and delegates here, so
 * element order follows registration order and a new readout never needs its own
 * layer. Each element is wrapped in {@link ModuleDispatcher#guard}, which means a
 * broken element is disabled instead of taking the HUD down with it.</p>
 */
public final class HudRenderer {

    private final ModuleRegistry registry;
    private final Setting<Boolean> backgroundEnabled;
    private final Setting<Integer> backgroundColor;
    private final Setting<Integer> textColor;

    private int renderedElements;
    private boolean loggedFirstFrame;

    /**
     * @param backgroundEnabled global switch for the backing plates
     * @param backgroundColor   backing plate colour, ARGB
     * @param textColor         default text colour, ARGB
     */
    public HudRenderer(ModuleRegistry registry, Setting<Boolean> backgroundEnabled, Setting<Integer> backgroundColor,
            Setting<Integer> textColor) {
        this.registry = registry;
        this.backgroundEnabled = backgroundEnabled;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
    }

    /** @return how many elements were drawn on the last frame */
    public int renderedElements() {
        return renderedElements;
    }

    /**
     * Renders one frame of the mod's HUD overlay.
     *
     * @param context the vanilla draw context for this frame
     */
    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }
        Window window = client.getWindow();
        if (window == null) {
            return;
        }

        HudContext hudContext = new HudContext(context, client.textRenderer,
                window.getScaledWidth(), window.getScaledHeight(),
                backgroundEnabled.get(), backgroundColor.get(), textColor.get());

        int drawn = 0;
        for (QoLModule module : registry.all()) {
            if (!module.isActive() || !(module instanceof HudElement element)) {
                continue;
            }
            if (ModuleDispatcher.guard(element, "hud render", () -> element.renderHud(hudContext))) {
                drawn++;
            }
        }
        renderedElements = drawn;

        if (!loggedFirstFrame) {
            loggedFirstFrame = true;
            WokeLite.LOGGER.info("HUD overlay rendering {} of {} registered modules in a {}x{} screen (background {})",
                    drawn, registry.size(), hudContext.screenWidth(), hudContext.screenHeight(),
                    hudContext.backgroundEnabled() ? "on" : "off");
        }
    }
}
