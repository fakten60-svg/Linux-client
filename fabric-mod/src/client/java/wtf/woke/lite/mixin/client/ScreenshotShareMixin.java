package wtf.woke.lite.mixin.client;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import wtf.woke.lite.WokeLiteClient;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.modules.ui.ScreenshotModule;

/**
 * Reports the exact screenshot file the game has just written.
 *
 * <p><b>Why Fabric API is not enough:</b> Fabric has no screenshot event, and
 * there is nothing to wrap instead: the file name is composed inside the
 * recorder — from the clock when the caller passes none — so the only place that
 * knows it is the very call that writes the file. Scanning the directory (which
 * the module also does, as a fallback) can only tell you that <em>some</em> file
 * appeared, one poll later; this tells the module which file it was, at the
 * moment it happened.</p>
 *
 * <p>The hook is deliberately observation-only: it lets the message the game
 * builds pass through untouched and reads the file name out of it, so with
 * {@code ui.screenshot} off — or if the message ever stops looking like this —
 * the recorder behaves exactly like vanilla and the directory scan still covers
 * the screenshot.</p>
 */
@Mixin(ScreenshotRecorder.class)
public abstract class ScreenshotShareMixin {

    /** Translation key the game uses for a screenshot it managed to write. */
    private static final String SUCCESS_KEY = "screenshot.success";

    /** Index of the message receiver in the recorder's five-argument save call. */
    private static final int RECEIVER_INDEX = 4;

    /**
     * Wraps the receiver so the module learns about a saved screenshot.
     *
     * <p>The recorder writes the file and calls this receiver on its own
     * file-writing thread, which is why the module only notes the file here and
     * does the clipboard and chat work on its next tick.</p>
     */
    @ModifyVariable(
            method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;Lnet/minecraft/client/gl/Framebuffer;"
                    + "ILjava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, index = RECEIVER_INDEX)
    private static Consumer<Text> wokewtf$watchSavedScreenshot(Consumer<Text> receiver) {
        ScreenshotModule module = wokewtf$activeScreenshotModule();
        if (module == null) {
            return receiver;
        }
        return message -> {
            receiver.accept(message);
            wokewtf$savedFileName(message).ifPresent(module::screenshotSaved);
        };
    }

    /**
     * @param message the message the game built for a screenshot
     * @return the name of the file it wrote, or empty for anything else — a
     *         failure message names no file and is left alone
     */
    private static Optional<String> wokewtf$savedFileName(Text message) {
        if (!(message.getContent() instanceof TranslatableTextContent translatable)
                || !SUCCESS_KEY.equals(translatable.getKey())) {
            return Optional.empty();
        }
        Object[] arguments = translatable.getArgs();
        if (arguments.length != 1 || !(arguments[0] instanceof Text fileName)) {
            return Optional.empty();
        }
        return Optional.of(fileName.getString());
    }

    /**
     * @return the screenshot module when it is registered and running, otherwise
     *         {@code null} — which the injection treats as "leave the recorder
     *         alone"
     */
    private static ScreenshotModule wokewtf$activeScreenshotModule() {
        ModuleRegistry registry = WokeLiteClient.registry();
        if (registry == null) {
            // ScreenshotRecorder is loaded before the client entrypoint has run.
            return null;
        }
        QoLModule module = registry.byId(ScreenshotModule.MODULE_ID).orElse(null);
        return module instanceof ScreenshotModule screenshot && screenshot.isActive() ? screenshot : null;
    }
}
