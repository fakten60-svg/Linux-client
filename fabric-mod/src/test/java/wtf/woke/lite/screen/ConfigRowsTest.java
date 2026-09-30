package wtf.woke.lite.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.screen.ConfigRows.ModuleText;
import wtf.woke.lite.screen.ConfigRows.SettingText;

/**
 * Pins what the config screen's row list does to a query.
 *
 * <p>The screen itself cannot be asserted on here, so every rule that decides
 * what a player sees — which modules survive a search, what a category badge
 * counts, whether an empty category leaves a header behind — is asserted on the
 * model instead.</p>
 */
class ConfigRowsTest {

    private static final ModuleText FPS = module("hud.fps", ModuleCategory.HUD, "FPS Counter",
            "Shows the current client frame rate.", true);
    private static final ModuleText CROSSHAIR = module("ui.crosshair", ModuleCategory.INTERFACE, "Crosshair",
            "Draws your own crosshair.", false);
    private static final ModuleText GROESSE = module("ui.groesse", ModuleCategory.INTERFACE, "Gr\u00f6\u00dfe",
            "Zeigt die Gr\u00f6\u00dfe des Blockes an.", false);
    private static final ModuleText RECONNECT = module("util.auto_reconnect", ModuleCategory.CONVENIENCE,
            "Auto Reconnect", "Reconnects after a dropped connection.", false);
    private static final List<ModuleText> ALL = List.of(FPS, CROSSHAIR, GROESSE, RECONNECT);

    private static ModuleText module(String id, ModuleCategory category, String name, String description,
            boolean enabled) {
        Setting<Boolean> toggle = Settings.bool("demo", enabled);
        return new ModuleText(id, category, name, description, enabled,
                List.of(new SettingText(toggle, "Demo", "A demo setting.")));
    }

    private static List<ConfigRow> visible(List<ConfigRow> rows) {
        return rows.stream().filter(row -> row instanceof ConfigRow.ModuleRow).toList();
    }

    private static List<String> visibleIds(List<ConfigRow> rows) {
        return visible(rows).stream().map(row -> ((ConfigRow.ModuleRow) row).id()).toList();
    }

    private static List<ModuleCategory> categories(List<ConfigRow> rows) {
        return rows.stream().filter(row -> row instanceof ConfigRow.CategoryRow)
                .map(row -> ((ConfigRow.CategoryRow) row).category()).toList();
    }

    @Test
    void aBlankQueryShowsEveryCategoryAndEveryModule() {
        List<ConfigRow> rows = ConfigRows.build(ALL, "", Set.of());

        assertEquals(List.of(ModuleCategory.HUD, ModuleCategory.INTERFACE, ModuleCategory.CONVENIENCE),
                categories(rows));
        assertEquals(List.of("hud.fps", "ui.crosshair", "ui.groesse", "util.auto_reconnect"), visibleIds(rows));
        // Each module contributes exactly one header plus its one setting row.
        assertEquals(3 + 4 * 2, rows.size());
    }

    @Test
    void anExactNameKeepsOnlyThatModule() {
        assertEquals(List.of("ui.crosshair"), visibleIds(ConfigRows.build(ALL, "Crosshair", Set.of())));
    }

    @Test
    void aPrefixKeepsEveryModuleItStarts() {
        List<ConfigRow> rows = ConfigRows.build(ALL, "Auto Re", Set.of());

        assertEquals(List.of("util.auto_reconnect"), visibleIds(rows));
        // The two categories that lost every module are gone entirely.
        assertEquals(List.of(ModuleCategory.CONVENIENCE), categories(rows));
    }

    @Test
    void aSubstringOfTheDescriptionKeepsTheModule() {
        assertEquals(List.of("util.auto_reconnect"), visibleIds(ConfigRows.build(ALL, "dropped", Set.of())));
        assertEquals(List.of("hud.fps"), visibleIds(ConfigRows.build(ALL, "frame rate", Set.of())));
    }

    @Test
    void matchingIgnoresCase() {
        for (String query : List.of("crosshair", "CROSSHAIR", "cRoSsHaIr")) {
            assertEquals(List.of("ui.crosshair"), visibleIds(ConfigRows.build(ALL, query, Set.of())), query);
        }
    }

    @Test
    void matchingIgnoresUmlauts() {
        // "\u00f6" on its own would fold to a plain "o" and match half the list,
        // so the query has to be long enough for the fold to mean anything.
        assertEquals(List.of("ui.groesse"), visibleIds(ConfigRows.build(ALL, "\u00f6\u00dfe", Set.of())));
        assertEquals(List.of("ui.groesse"), visibleIds(ConfigRows.build(ALL, "GR\u00d6\u00dfE", Set.of())));
        assertEquals(List.of("ui.groesse"), visibleIds(ConfigRows.build(ALL, "grosse", Set.of())));
    }

    @Test
    void aCategoryWithNoMatchIsDroppedButItsSiblingsStay() {
        List<ConfigRow> rows = ConfigRows.build(ALL, "fps", Set.of());

        assertEquals(List.of(ModuleCategory.HUD), categories(rows));
        assertEquals(List.of("hud.fps"), visibleIds(rows));
    }

    @Test
    void aQueryThatMatchesNothingLeavesNoRows() {
        assertEquals(List.of(), ConfigRows.build(ALL, "zzzz", Set.of()));
        assertEquals(0, ConfigRows.contentHeight(ConfigRows.build(ALL, "zzzz", Set.of())));
    }

    @Test
    void aCollapsedCategoryKeepsItsHeaderAndDropsItsModules() {
        List<ConfigRow> rows = ConfigRows.build(ALL, "", EnumSet.of(ModuleCategory.INTERFACE));

        ConfigRow.CategoryRow header = rows.stream()
                .filter(row -> row instanceof ConfigRow.CategoryRow)
                .map(row -> (ConfigRow.CategoryRow) row)
                .filter(row -> row.category() == ModuleCategory.INTERFACE)
                .findFirst()
                .orElseThrow();
        assertTrue(header.collapsed());
        assertEquals(List.of("hud.fps", "util.auto_reconnect"), visibleIds(rows));
    }

    @Test
    void aFilteredBadgeCountsWhatSurvivedOutOfEverything() {
        List<ConfigRow> rows = ConfigRows.build(ALL, "crosshair", Set.of());
        ConfigRow.CategoryRow header = (ConfigRow.CategoryRow) rows.get(0);

        assertEquals(1, header.shown());
        assertEquals(2, header.total(), "both Interface modules are registered");
        assertTrue(header.isFiltered());

        ConfigRow.CategoryRow unfiltered = (ConfigRow.CategoryRow) ConfigRows.build(ALL, "", Set.of()).get(0);
        assertFalse(unfiltered.isFiltered(), "a badge without a query reports the plain total");
    }

    @Test
    void settingRowsFollowTheirOwnModuleAndHaveUniqueKeys() {
        List<ConfigRow> rows = ConfigRows.build(ALL, "fps", Set.of());

        assertEquals(List.of("module:hud.fps", "setting:hud.fps/demo"),
                rows.stream().filter(row -> !(row instanceof ConfigRow.CategoryRow)).map(ConfigRow::key).toList());
        List<String> allKeys = ConfigRows.build(ALL, "", Set.of()).stream().map(ConfigRow::key).toList();
        assertEquals(allKeys.size(), allKeys.stream().collect(Collectors.toSet()).size());
    }

    @Test
    void theContentHeightIsTheSumOfTheRowHeights() {
        List<ConfigRow> rows = ConfigRows.build(ALL, "", Set.of());

        int expected = categories(rows).size() * ConfigRow.CATEGORY_HEIGHT
                + 4 * ConfigRow.MODULE_HEIGHT + 4 * ConfigRow.SETTING_HEIGHT;
        assertEquals(expected, ConfigRows.contentHeight(rows));
    }

    @Test
    void collapsingReturnsANewSetAndLeavesTheOldOneAlone() {
        Set<ModuleCategory> original = EnumSet.of(ModuleCategory.HUD);

        Set<ModuleCategory> folded = ConfigRows.withCollapsed(original, ModuleCategory.INTERFACE, true);
        assertTrue(folded.contains(ModuleCategory.HUD));
        assertTrue(folded.contains(ModuleCategory.INTERFACE));
        assertEquals(Set.of(ModuleCategory.HUD), original, "the caller's set must not change");

        Set<ModuleCategory> opened = ConfigRows.withCollapsed(folded, ModuleCategory.HUD, false);
        assertFalse(opened.contains(ModuleCategory.HUD));
        assertTrue(opened.contains(ModuleCategory.INTERFACE));

        Set<ModuleCategory> fromNull = ConfigRows.withCollapsed(null, ModuleCategory.HUD, true);
        assertEquals(Set.of(ModuleCategory.HUD), fromNull);
    }

}
