package wtf.woke.lite;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.testutil.LanguageFile;

/**
 * Guards the English text of the four modules the other language tests do not
 * reach: the HUD readout and the three interface modules.
 *
 * <p>The convenience modules have their own test and the two HUD list features
 * are asserted next door; without this file a renamed key on {@code hud.fps} or
 * {@code ui.crosshair} would only show up as the raw key in the settings screen.
 * Each module needs a name, a description, the two placement settings every
 * HUD element declares, and a label for each of its own settings.</p>
 */
class WokeLiteBuiltinLanguageTest {

    private static final String HUD = "wokewtf.lite.module.hud.";
    private static final String UI = "wokewtf.lite.module.ui.";

    private static String module(String prefix, String id) {
        return prefix + id;
    }

    private static void addLabels(List<String> keys, String module, String setting, boolean tooltip) {
        keys.add(module + ".setting." + setting);
        if (tooltip) {
            keys.add(module + ".setting." + setting + ".tooltip");
        }
    }

    /** The two placement settings {@code HudElement} adds to every readout. */
    private static void addLayoutSettings(List<String> keys, String module) {
        addLabels(keys, module, "anchor", true);
        addLabels(keys, module, "offsetX", false);
        addLabels(keys, module, "offsetY", false);
    }

    @Test
    void everyBuiltinModuleHasANameAndADescription() {
        List<String> keys = new ArrayList<>();
        for (String id : List.of("fps")) {
            keys.add(module(HUD, id));
            keys.add(module(HUD, id) + ".description");
        }
        for (String id : List.of("crosshair", "chat", "book")) {
            keys.add(module(UI, id));
            keys.add(module(UI, id) + ".description");
        }

        LanguageFile.assertTranslated(LanguageFile.load(), keys);
    }

    @Test
    void theFpsCounterHasText() {
        String fps = module(HUD, "fps");
        List<String> keys = new ArrayList<>();
        addLayoutSettings(keys, fps);
        addLabels(keys, fps, "showLabel", false);

        LanguageFile.assertTranslated(LanguageFile.load(), keys);
    }

    @Test
    void theCrosshairHasText() {
        String crosshair = module(UI, "crosshair");
        List<String> keys = new ArrayList<>();
        addLabels(keys, crosshair, "style", true);
        addLabels(keys, crosshair, "color", false);
        addLabels(keys, crosshair, "thickness", true);
        addLabels(keys, crosshair, "gap", true);
        addLabels(keys, crosshair, "length", false);
        addLabels(keys, crosshair, "outline", false);
        addLabels(keys, crosshair, "outlineColor", false);

        LanguageFile.assertTranslated(LanguageFile.load(), keys);
    }

    @Test
    void theChatModuleHasText() {
        String chat = module(UI, "chat");
        List<String> keys = new ArrayList<>();
        addLabels(keys, chat, "timestamps", true);
        addLabels(keys, chat, "timestampStyle", false);
        addLabels(keys, chat, "historyLength", true);

        LanguageFile.assertTranslated(LanguageFile.load(), keys);
    }

    @Test
    void theBookCounterHasText() {
        String book = module(UI, "book");
        List<String> keys = new ArrayList<>();
        addLabels(keys, book, "counter", true);
        addLabels(keys, book, "color", false);

        LanguageFile.assertTranslated(LanguageFile.load(), keys);
    }
}
