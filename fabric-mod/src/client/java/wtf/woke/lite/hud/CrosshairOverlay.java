package wtf.woke.lite.hud;

import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.ModuleDispatcher;
import wtf.woke.lite.modules.ui.CrosshairModule;

/**
 * Draws the module's crosshair in place of the vanilla one.
 *
 * <p><b>Why this needs no mixin:</b> Fabric's HUD registry already offers the
 * exact hook — {@code HudElementRegistry.replaceElement} hands the replaced
 * element to a function — so the vanilla crosshair is kept as a delegate and
 * replayed verbatim on every frame this module does not own. Nothing imitates
 * the vanilla render path, and nothing has to patch it.</p>
 *
 * <p>The delegate also carries the vanilla element's render condition, so cases
 * such as the spectator menu, a loading screen or a hidden HUD (F1) keep working
 * without this class knowing about them.</p>
 */
public final class CrosshairOverlay implements HudElement {

    private final CrosshairModule module;

    private HudElement vanillaElement;
    private boolean loggedFirstDraw;

    /**
     * @param module the module whose settings decide what is drawn
     */
    public CrosshairOverlay(CrosshairModule module) {
        this.module = Objects.requireNonNull(module, "module");
    }

    /**
     * The function handed to
     * {@link net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry#replaceElement}.
     *
     * <p>Fabric calls it on every frame with the element that would have drawn,
     * and renders whatever it returns, so this both records the delegate and
     * returns the replacement.</p>
     *
     * @param original the element being replaced, i.e. the vanilla crosshair
     * @return this overlay
     */
    public HudElement replacing(HudElement original) {
        this.vanillaElement = original;
        return this;
    }

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean drawCustom = module.isActive() && client != null && !client.options.hudHidden;

        // hudHidden is checked again even though vanilla only calls this element
        // from inside its own guard: that guard is an implementation detail we do
        // not control, and a crosshair floating on a hidden HUD would be a bug.
        if (!drawCustom || !ModuleDispatcher.guard(module, "crosshair render", () -> draw(context))) {
            renderVanilla(context, tickCounter);
        }
    }

    private void renderVanilla(DrawContext context, RenderTickCounter tickCounter) {
        if (vanillaElement != null) {
            vanillaElement.render(context, tickCounter);
        }
    }

    private void draw(DrawContext context) {
        CrosshairGeometry geometry = module.geometry();
        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;
        List<HudBounds> arms = geometry.arms(centerX, centerY);
        int color = module.color();
        boolean outline = module.outlineEnabled();
        int outlineColor = module.outlineColor();

        for (HudBounds arm : arms) {
            if (outline) {
                HudBounds border = arm.expanded(1);
                context.fill(border.x(), border.y(), border.right(), border.bottom(), outlineColor);
            }
            context.fill(arm.x(), arm.y(), arm.right(), arm.bottom(), color);
        }

        if (!loggedFirstDraw) {
            loggedFirstDraw = true;
            WokeLite.LOGGER.info("Crosshair override drawing {} with {} arm(s) at ({}, {}) in a {}x{} screen",
                    geometry.style(), arms.size(), centerX, centerY,
                    context.getScaledWindowWidth(), context.getScaledWindowHeight());
        }
    }
}
