package wtf.woke.lite.screen;

import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.Setting;

/**
 * One line of the config screen, as data.
 *
 * <p>The screen is the only thing that can draw, so nothing in here knows about
 * it: a row says what it is, what it is called and how tall it is, and the
 * client turns that into widgets. Keeping the model plain is what makes the
 * parts worth testing — which rows a query leaves standing, what a category
 * badge counts, how tall the list is — testable without a game.</p>
 *
 * <p>Heights include the row's own vertical padding, so the list can lay itself
 * out by summing them and there is exactly one place that decides spacing.</p>
 */
public sealed interface ConfigRow {

    /** Category header: the collapsible bar with the module count. */
    int CATEGORY_HEIGHT = 22;

    /** Module header: its name and description. */
    int MODULE_HEIGHT = 20;

    /** One setting of a module. */
    int SETTING_HEIGHT = 16;

    /** @return the stable identity the screen maps a widget onto */
    String key();

    /** @return this row's height in GUI units, padding included */
    int height();

    /**
     * A collapsible category header.
     *
     * @param category  which category this bar opens
     * @param shown     how many of its modules survived the current query
     * @param total     how many modules it has in total
     * @param collapsed whether its modules are currently hidden
     */
    record CategoryRow(ModuleCategory category, int shown, int total, boolean collapsed) implements ConfigRow {

        @Override
        public String key() {
            return "category:" + category.slug();
        }

        @Override
        public int height() {
            return CATEGORY_HEIGHT;
        }

        /** @return whether the badge has to say "3 of 8" rather than just "8" */
        public boolean isFiltered() {
            return shown != total;
        }
    }

    /**
     * A module header.
     *
     * <p>Carries the resolved name and description rather than a translation
     * key, because the query is matched against the text a player actually
     * reads, and that text only exists once the language file has answered.</p>
     *
     * @param id          the module's stable id
     * @param name        the module's translated name
     * @param description the module's translated one-line description
     * @param enabled     whether the module is switched on
     */
    record ModuleRow(String id, String name, String description, boolean enabled) implements ConfigRow {

        @Override
        public String key() {
            return "module:" + id;
        }

        @Override
        public int height() {
            return MODULE_HEIGHT;
        }
    }

    /**
     * One setting of one module.
     *
     * @param moduleId the module that owns the setting
     * @param setting  the live setting, so the row can read and reset it
     * @param label    the setting's translated name
     * @param tooltip  the setting's translated description, empty when it has none
     */
    record SettingRow(String moduleId, Setting<?> setting, String label, String tooltip) implements ConfigRow {

        @Override
        public String key() {
            return "setting:" + moduleId + "/" + setting.id();
        }

        @Override
        public int height() {
            return SETTING_HEIGHT;
        }

        /** @return whether hovering this row shows a tooltip */
        public boolean hasTooltip() {
            return !tooltip.isBlank();
        }
    }
}
