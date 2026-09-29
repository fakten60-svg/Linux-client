package wtf.woke.lite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.book.BookCounterMode;
import wtf.woke.lite.chat.ChatTimestampStyle;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.hud.CrosshairStyle;
import wtf.woke.lite.inventory.SortOrder;
import wtf.woke.lite.inventory.SortResult;
import wtf.woke.lite.testutil.LanguageFile;
import wtf.woke.lite.waypoint.WaypointResult;

/**
 * Guards the link between the keys the code asks for and the language file the
 * mod ships.
 *
 * <p>A key with no translation is not an error the game reports: it silently
 * shows the raw key, which is how a renamed setting ends up looking broken only
 * to whoever reads the config screen. These assertions come from the enums
 * themselves, so they follow a rename instead of pinning it.</p>
 *
 * <p>The convenience modules have their own file, so neither test has to hold
 * the whole language file's worth of keys at once.</p>
 */
class WokeLiteLanguageTest {

    @Test
    void everyEnumLabelHasEnglishText() {
        List<String> keys = new ArrayList<>();
        for (ModuleCategory value : ModuleCategory.values()) {
            keys.add(value.translationKey());
        }
        for (CrosshairStyle value : CrosshairStyle.values()) {
            keys.add(value.translationKey());
        }
        for (ChatTimestampStyle value : ChatTimestampStyle.values()) {
            keys.add(value.translationKey());
        }
        for (BookCounterMode value : BookCounterMode.values()) {
            keys.add(value.translationKey());
            keys.add(value.labelKey());
        }
        for (SortOrder value : SortOrder.values()) {
            keys.add(value.translationKey());
        }
        for (SortResult value : SortResult.values()) {
            keys.add(value.translationKey());
        }
        for (WaypointResult value : WaypointResult.values()) {
            keys.add(value.translationKey());
        }

        LanguageFile.assertTranslated(LanguageFile.load(), keys);
    }

    @Test
    void theShippedFeaturesAndTheirCommandStringsHaveEnglishText() {
        LanguageFile.assertTranslated(LanguageFile.load(), List.of(
                "wokewtf.lite.module.ui.screenshot",
                "wokewtf.lite.module.ui.screenshot.description",
                "wokewtf.lite.module.ui.screenshot.setting.copyPath",
                "wokewtf.lite.module.ui.screenshot.setting.copyPath.tooltip",
                "wokewtf.lite.module.ui.screenshot.setting.notify",
                "wokewtf.lite.module.ui.screenshot.setting.shareLink",
                "wokewtf.lite.module.ui.screenshot.setting.shareLink.tooltip",
                "wokewtf.lite.module.ui.screenshot.saved",
                "wokewtf.lite.module.ui.screenshot.shared",
                "wokewtf.lite.module.ui.waypoints",
                "wokewtf.lite.module.ui.waypoints.description",
                "wokewtf.lite.module.ui.waypoints.setting.waypoints",
                "wokewtf.lite.module.ui.waypoints.setting.maxShown",
                "wokewtf.lite.module.ui.waypoints.setting.showCoordinates",
                "wokewtf.lite.module.ui.waypoints.setting.anchor",
                "wokewtf.lite.module.ui.waypoints.setting.anchor.tooltip",
                "wokewtf.lite.module.ui.waypoints.setting.offsetX",
                "wokewtf.lite.module.ui.waypoints.setting.offsetY",
                "wokewtf.lite.module.ui.waypoints.line",
                "wokewtf.lite.module.ui.waypoints.line.withCoordinates",
                "wokewtf.lite.module.ui.inventory_sort",
                "wokewtf.lite.module.ui.inventory_sort.description",
                "wokewtf.lite.module.ui.inventory_sort.setting.order",
                "wokewtf.lite.module.ui.inventory_sort.setting.order.tooltip",
                "wokewtf.lite.module.ui.inventory_sort.setting.includeHotbar",
                "wokewtf.lite.module.ui.inventory_sort.setting.includeHotbar.tooltip",
                "wokewtf.lite.module.ui.inventory_sort.done",
                "wokewtf.lite.module.ui.inventory_sort.interrupted",
                "wokewtf.lite.command.waypoint.list.empty",
                "wokewtf.lite.command.waypoint.list.header",
                "wokewtf.lite.command.waypoint.list.entry",
                "wokewtf.lite.reason.no_world",
                "wokewtf.lite.reason.own_world_only"));
    }

    @Test
    void everyKeyBelongsToThisMod() {
        List<String> foreign = LanguageFile.load().keySet().stream()
                .filter(key -> !key.startsWith(LanguageFile.MOD_PREFIX))
                .filter(key -> !key.startsWith(LanguageFile.KEYBIND_CATEGORY_PREFIX))
                .toList();

        assertEquals(List.of(), foreign, "language keys must stay under the mod's own prefix");
        assertTrue(LanguageFile.load().has(LanguageFile.KEYBIND_CATEGORY_PREFIX + "main"),
                "the keybind category label must exist under the key the game builds");
        assertTrue(LanguageFile.load().size() > 50, "the language file should cover the shipped features");
    }
}
