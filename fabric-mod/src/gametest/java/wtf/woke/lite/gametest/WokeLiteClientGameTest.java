package wtf.woke.lite.gametest;

import java.awt.image.BufferedImage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import wtf.woke.lite.WokeLiteClient;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.modules.hud.FpsModule;
import wtf.woke.lite.modules.ui.ChatModule;
import wtf.woke.lite.modules.ui.CrosshairModule;
import wtf.woke.lite.screen.ConfigTheme;

/**
 * A dev-only client gametest that renders the mod for real and then reads the
 * pixels back.
 *
 * <p>It exists because the manual session is the only other place the drawn
 * result is checked, and a person staring at a screen is the one thing about
 * this mod that cannot be re-run by a machine. It is not part of the mod: it
 * lives in its own source set with its own test mod, is never packaged, adds no
 * runtime dependency the mod itself would carry, and talks to nothing but the
 * running client.</p>
 *
 * <p>Every check is a pixel count of a colour the test itself picked, so a
 * failure can say how many pixels it wanted and how many it saw. The two probe
 * colours are deliberately garish; the world, and the config file, are put back
 * afterwards.</p>
 */
@SuppressWarnings("UnstableApiUsage")
public final class WokeLiteClientGameTest implements FabricClientGameTest {

    /** The one chat line both probes put in the chat box. */
    private static final String CHAT_PROBE_TEXT = "gametest chat probe";

    @Override
    public void runTest(ClientGameTestContext context) {
        GameTestReport report = new GameTestReport();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            prepare(context);
            context.waitTicks(20);
            checkFpsPlate(context, report);
            checkCrosshair(context, report);
            // Let the world finish settling before the chat probes: those compare
            // two frames pixel by pixel and need the terrain behind the chat to
            // be the same in both.
            world.getClientWorld().waitForChunksRender();
            context.waitTicks(40);
            checkChatTimestamp(context, report);
            checkSettingsScreen(context, report);
            restore(context);
        }
        report.summarise();
    }

    /** Everything off, then the few modules and probe colours this test needs. */
    private static void prepare(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ModuleRegistry registry = WokeLiteClient.registry();
            for (QoLModule module : registry.all()) {
                registry.setEnabled(module.id(), false);
            }
            registry.setEnabled(FpsModule.MODULE_ID, true);
            registry.setEnabled(CrosshairModule.MODULE_ID, true);
            registry.setEnabled(ChatModule.MODULE_ID, true);
            GametestSupport.globalSetting("hud.background").fromRaw(true);
            GametestSupport.globalSetting("hud.backgroundColor").fromRaw(GametestSupport.PROBE_PLATE);
            GametestSupport.setting(CrosshairModule.MODULE_ID, "color").fromRaw(GametestSupport.PROBE_CROSSHAIR);
            GametestSupport.setting(CrosshairModule.MODULE_ID, "style").fromRaw("dot");
            GametestSupport.setting(ChatModule.MODULE_ID, "timestamps").fromRaw(false);
        });
    }

    /** Puts the settings and the config file back the way they were. */
    private static void restore(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.setScreen(null);
            WokeLiteClient.config().globalSettings().forEach(Setting::reset);
            ModuleRegistry registry = WokeLiteClient.registry();
            for (QoLModule module : registry.all()) {
                registry.setEnabled(module.id(), false);
                module.settings().forEach(Setting::reset);
            }
        });
        context.waitTicks(20);
    }

    private static void checkFpsPlate(ClientGameTestContext context, GameTestReport report) {
        BufferedImage image = GametestSupport.screenshot(context, "wokewtf-lite-hud", report);
        if (image == null) {
            return;
        }
        int window = 48 * GametestSupport.scale(context);
        int matches = PixelChecks.countNear(image, GametestSupport.PROBE_PLATE, 2, 0, 0, window, window);
        report.check("fps-counter-plate-in-top-left", matches >= 20, "plate pixels "
                + PixelChecks.hex(GametestSupport.PROBE_PLATE) + " in the top-left " + window + "px: " + matches);
    }

    private static void checkCrosshair(ClientGameTestContext context, GameTestReport report) {
        BufferedImage image = GametestSupport.screenshot(context, "wokewtf-lite-crosshair", report);
        if (image == null) {
            return;
        }
        int radius = 6 * GametestSupport.scale(context);
        int centerX = image.getWidth() / 2;
        int centerY = image.getHeight() / 2;
        int matches = PixelChecks.countNear(image, GametestSupport.PROBE_CROSSHAIR, 0,
                centerX - radius, centerY - radius, centerX + radius + 1, centerY + radius + 1);
        report.check("crosshair-centre-has-configured-colour", matches >= 1,
                "crosshair pixels " + PixelChecks.hex(GametestSupport.PROBE_CROSSHAIR) + " at the screen centre: "
                        + matches);
    }

    private static void checkChatTimestamp(ClientGameTestContext context, GameTestReport report) {
        // Two screenshots of the same world, each with exactly one chat line of
        // the SAME text: one line without a timestamp, one with. Same text means
        // the only extra pixels the second frame can show are the timestamp's.
        Integer without = GametestSupport.chatProbe(context, report, false, CHAT_PROBE_TEXT);
        Integer with = GametestSupport.chatProbe(context, report, true, CHAT_PROBE_TEXT);
        if (without == null || with == null) {
            return;
        }
        // The timestamp is eight or so glyphs wider than nothing, so a handful of
        // extra text pixels is the floor; a pixel or two would not be evidence.
        report.check("chat-timestamp-drawn-in-chat-area", with >= without + 10,
                "chat text pixels: " + without + " without vs " + with + " with a timestamp");
    }

    private static void checkSettingsScreen(ClientGameTestContext context, GameTestReport report) {
        context.runOnClient(client -> client.getNetworkHandler().sendChatCommand("wokewtf config"));
        // Wait for the command to open the screen rather than asserting: a timeout
        // has to end up as a FAIL line like every other check.
        for (int tick = 0; tick < 100 && !GametestSupport.configScreenOpen(context); tick++) {
            context.waitTick();
        }
        boolean open = GametestSupport.configScreenOpen(context);
        report.check("settings-screen-opens-via-command", open, "current screen is WokeConfigScreen: " + open);
        if (!open) {
            return;
        }
        context.waitTicks(5);
        BufferedImage image = GametestSupport.screenshot(context, "wokewtf-lite-settings-screen", report);
        if (image == null) {
            return;
        }
        int scale = GametestSupport.scale(context);
        int panel = GametestSupport.sidebarPixels(image, ConfigTheme.PANEL, scale);
        int accent = GametestSupport.sidebarPixels(image, ConfigTheme.ACCENT, scale);
        report.check("settings-screen-sidebar-visible", panel >= 50 && accent >= 4,
                "sidebar pixels: panel " + panel + ", accent " + accent);
    }
}
