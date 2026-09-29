package wtf.woke.lite.mixin.client;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.woke.lite.WokeLiteClient;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.modules.ui.BookModule;

/**
 * Draws the book module's page counter on the writing screen.
 *
 * <p><b>Why Fabric API is not enough:</b> the screen's page list and current
 * page index are private fields, and no Fabric screen event exposes them — the
 * render events hand over a draw context and nothing else. Reading the state
 * the counter counts therefore needs a {@code @Shadow} on the real fields, and
 * while that is in place, painting the counter at the end of the screen's own
 * render keeps it in the same layer as the page it describes.</p>
 *
 * <p>The injection only ever draws: it does not touch the page list, the
 * current page, the text field or any input, so a signed book is byte for byte
 * what vanilla would have produced.</p>
 */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {

    @Shadow
    @Final
    private List<String> pages;

    @Shadow
    private int currentPage;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
    private void wokewtf$renderPageCounter(DrawContext context, int mouseX, int mouseY, float tickDelta,
            CallbackInfo callbackInfo) {
        BookModule book = wokewtf$activeBookModule();
        if (book == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        book.statsLabel(currentPage, pages).ifPresent(label -> wokewtf$draw(context, client, label));
    }

    /**
     * Paints the counter under the book, aligned with its left edge. The
     * position is derived from the screen's public width constant rather than
     * from its private layout helpers, so a mismatch can only move the text,
     * never break the screen.
     */
    private void wokewtf$draw(DrawContext context, MinecraftClient client, Text label) {
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int x = (screenWidth - BookEditScreen.WIDTH) / 2;
        int y = screenHeight - 12;
        context.drawTextWithShadow(client.textRenderer, label, x, y, 0xFFFFFFFF);
    }

    private static BookModule wokewtf$activeBookModule() {
        ModuleRegistry registry = WokeLiteClient.registry();
        if (registry == null) {
            return null;
        }
        QoLModule module = registry.byId(BookModule.MODULE_ID).orElse(null);
        return module instanceof BookModule book && book.isActive() ? book : null;
    }
}
