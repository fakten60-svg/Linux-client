package wtf.woke.lite.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.text.Text;
import wtf.woke.lite.WokeLiteClient;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.modules.ui.ChatModule;
import wtf.woke.lite.screen.WokeConfigScreen;

/**
 * The probe colours, screen boxes and one-line helpers the client gametest
 * checks share.
 *
 * <p>Kept apart from the checks themselves so both files stay readable, and
 * because every check reads a screenshot and a GUI box the same way — one place
 * deciding that is one place to get the GUI-to-pixel scaling right.</p>
 */
final class GametestSupport {

    /** Backing-plate colour forced onto the HUD, so the plate is an exact match. */
    static final int PROBE_PLATE = 0xFFFF00FF;

    /** Crosshair colour forced onto the module, so the centre pixel is exact. */
    static final int PROBE_CROSSHAIR = 0xFFFF0000;

    /**
     * How bright a channel has to be to count as text rather than the dark
     * world behind the chat. The chat hud draws text at partial opacity while it
     * is unfocused, so vanilla's near-white arrives as a mid grey; the terrain
     * and the chat plate around it stay far below this.
     */
    static final int TEXT_MIN_CHANNEL = 0x40;

    /** The chat sits above the hotbar; its band is given in GUI units. */
    static final int CHAT_BAND_BOTTOM_ABOVE_HOTBAR = 30;
    static final int CHAT_BAND_HEIGHT = 60;

    /** The config screen's sidebar, in GUI units (see WokeConfigScreen). */
    static final int SIDEBAR_LEFT = 12;
    static final int SIDEBAR_RIGHT = 148;
    static final int SIDEBAR_TOP = 31;
    static final int SIDEBAR_BOTTOM = 120;

    private GametestSupport() {
        throw new AssertionError("No instances of " + GametestSupport.class.getName());
    }

    /** @return the GUI scale, i.e. how many framebuffer pixels one GUI unit is */
    static int scale(ClientGameTestContext context) {
        return Math.max(1, context.computeOnClient(client -> client.getWindow().getScaleFactor()));
    }

    /** @return how many pixels of the sidebar box match {@code color} exactly */
    static int sidebarPixels(BufferedImage image, int color, int scale) {
        return PixelChecks.countNear(image, color, 0, SIDEBAR_LEFT * scale, SIDEBAR_TOP * scale,
                SIDEBAR_RIGHT * scale, Math.min(image.getHeight(), SIDEBAR_BOTTOM * scale));
    }

    static boolean configScreenOpen(ClientGameTestContext context) {
        return context.computeOnClient(client -> client.currentScreen instanceof WokeConfigScreen);
    }

    /** @return the screenshot as an image, or {@code null} after reporting why not */
    static BufferedImage screenshot(ClientGameTestContext context, String name, GameTestReport report) {
        Path path = context.takeScreenshot(name);
        try {
            return PixelChecks.read(path);
        } catch (IOException failure) {
            report.blocked(name, failure.toString());
            return null;
        }
    }

    /**
     * Clears the chat, puts one line in it, and counts the timestamp-grey pixels
     * the chat area then shows.
     *
     * @return the count, or {@code null} after reporting a screenshot it could not
     *         read
     */
    static Integer chatProbe(ClientGameTestContext context, GameTestReport report, boolean timestamps,
            String message) {
        context.runOnClient(client -> {
            setting(ChatModule.MODULE_ID, "timestamps").fromRaw(timestamps);
            client.inGameHud.getChatHud().clear(true);
            client.inGameHud.getChatHud().addMessage(Text.literal(message));
        });
        context.waitTicks(10);
        BufferedImage image = screenshot(context, "wokewtf-lite-chat-" + (timestamps ? "on" : "off"), report);
        if (image == null) {
            return null;
        }
        return chatTextPixels(image, scale(context));
    }

    /**
     * @return how many text pixels the chat band holds
     *
     * <p>The band is the lower-left, minus the strip the hotbar occupies: the
     * hotbar is bright and would otherwise be counted as chat, and it does not
     * change between the two probes, which is exactly the kind of constant that
     * would make a broken check look stable.</p>
     */
    private static int chatTextPixels(BufferedImage image, int scale) {
        int guiHeight = image.getHeight() / scale;
        int bottom = (guiHeight - CHAT_BAND_BOTTOM_ABOVE_HOTBAR) * scale;
        int top = bottom - CHAT_BAND_HEIGHT * scale;
        return PixelChecks.countBrighterThan(image, TEXT_MIN_CHANNEL, 0, top, image.getWidth() / 2, bottom);
    }

    static Setting<?> globalSetting(String id) {
        return WokeLiteClient.config().globalSetting(id).orElseThrow();
    }

    static Setting<?> setting(String moduleId, String settingId) {
        return WokeLiteClient.registry().byId(moduleId).orElseThrow().setting(settingId).orElseThrow();
    }
}
