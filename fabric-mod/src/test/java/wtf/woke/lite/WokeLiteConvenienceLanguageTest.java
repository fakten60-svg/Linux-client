package wtf.woke.lite;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.macro.ChatMacroList;
import wtf.woke.lite.testutil.LanguageFile;

/**
 * Guards the English text of the convenience modules.
 *
 * <p>Every module needs a name, a description and a line for each of its
 * settings, and every keybind needs a label or the Controls screen shows the
 * raw key. The keys are built from the same shapes the modules use, so adding a
 * setting to one of these modules without adding its text fails here rather
 * than in game.</p>
 */
class WokeLiteConvenienceLanguageTest {

    private static final String AUTO_RECONNECT = "auto_reconnect";
    private static final String RESPAWN_CONFIRM = "respawn_confirm";
    private static final String CHAT_MACROS = "chat_macros";
    private static final String SCREENSHOT_KEY = "screenshot_key";
    private static final String FULLSCREEN_KEY = "fullscreen_key";
    private static final String STATS = "stats";

    private static String module(String id) {
        return "wokewtf.lite.module.util." + id;
    }

    private static void addSetting(List<String> keys, String moduleId, String settingId, boolean hasTooltip) {
        keys.add(module(moduleId) + ".setting." + settingId);
        if (hasTooltip) {
            keys.add(module(moduleId) + ".setting." + settingId + ".tooltip");
        }
    }

    @Test
    void everyConvenienceModuleHasANameAndADescription() {
        List<String> keys = new ArrayList<>();
        for (String id : List.of(AUTO_RECONNECT, RESPAWN_CONFIRM, CHAT_MACROS, SCREENSHOT_KEY, FULLSCREEN_KEY, STATS)) {
            keys.add(module(id));
            keys.add(module(id) + ".description");
        }

        LanguageFile.assertTranslated(LanguageFile.load(), keys);
    }

    @Test
    void everyConvenienceSettingHasText() {
        List<String> keys = new ArrayList<>();
        addSetting(keys, AUTO_RECONNECT, "countdownSeconds", true);
        addSetting(keys, RESPAWN_CONFIRM, "delaySeconds", true);
        addSetting(keys, CHAT_MACROS, "macros", true);
        addSetting(keys, STATS, "blocksMined", false);
        addSetting(keys, STATS, "distanceWalked", false);
        addSetting(keys, STATS, "playTimeTicks", false);

        LanguageFile.assertTranslated(LanguageFile.load(), keys);
    }

    @Test
    void everyConvenienceKeybindHasALabel() {
        List<String> keys = new ArrayList<>(List.of(
                module(AUTO_RECONNECT) + ".keybind.cancel",
                module(SCREENSHOT_KEY) + ".keybind",
                module(FULLSCREEN_KEY) + ".keybind"));
        for (int slot = 1; slot <= ChatMacroList.MAX_ENTRIES; slot++) {
            keys.add(module(CHAT_MACROS) + ".keybind.slot" + slot);
        }

        LanguageFile.assertTranslated(LanguageFile.load(), keys);
    }

    @Test
    void everyConvenienceMessageHasText() {
        List<String> keys = new ArrayList<>(List.of(
                module(AUTO_RECONNECT) + ".lost",
                module(AUTO_RECONNECT) + ".countdown",
                module(AUTO_RECONNECT) + ".connecting",
                module(AUTO_RECONNECT) + ".cancelled",
                module(AUTO_RECONNECT) + ".timeout",
                module(AUTO_RECONNECT) + ".failed",
                module(RESPAWN_CONFIRM) + ".waiting",
                module(CHAT_MACROS) + ".empty",
                "wokewtf.lite.keybind.conflict"));
        for (String outcome : List.of("stored", "replaced", "removed", "not_found", "invalid_name", "invalid_text",
                "full", "list.empty", "list.header", "list.entry")) {
            keys.add("wokewtf.lite.command.macro." + outcome);
        }
        for (String part : List.of("header", "blocks", "distance", "playtime", "reset")) {
            keys.add("wokewtf.lite.command.stats." + part);
        }

        LanguageFile.assertTranslated(LanguageFile.load(), keys);
    }
}
