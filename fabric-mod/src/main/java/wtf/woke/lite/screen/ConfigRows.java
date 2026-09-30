package wtf.woke.lite.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.search.ModuleSearch;

/**
 * Turns the registered modules into the flat list of rows the config screen
 * shows.
 *
 * <p>Three rules live here, and all three are the kind that go wrong quietly in
 * a screen nobody can unit-test: which rows a query leaves standing, what a
 * category badge counts, and how tall the resulting list is. Being outside the
 * client means a test can assert on them directly.</p>
 *
 * <p>The order is fixed by the framework, not by the data: categories follow
 * {@link ModuleCategory}'s declaration order and modules follow registration
 * order, so the screen does not reshuffle itself when a module is added.</p>
 */
public final class ConfigRows {

    /**
     * A module as the screen presents it.
     *
     * @param id          the module's stable id
     * @param category    the category it is grouped under
     * @param name        its translated name
     * @param description its translated one-line description
     * @param enabled     whether it is switched on
     * @param settings    its settings, in declaration order
     */
    public record ModuleText(String id, ModuleCategory category, String name, String description, boolean enabled,
            List<SettingText> settings) {

        public ModuleText {
            settings = List.copyOf(settings);
        }
    }

    /**
     * A setting with its text already resolved.
     *
     * @param setting the live setting
     * @param label   its translated name
     * @param tooltip its translated description, empty when it has none
     */
    public record SettingText(Setting<?> setting, String label, String tooltip) {
    }

    /**
     * A row together with the offset it sits at in a list.
     *
     * @param row    the row
     * @param y      its top, measured from the list's top edge
     * @param height its height, the same value {@link ConfigRow#height} reports
     */
    public record Placed(ConfigRow row, int y, int height) {
    }

    private ConfigRows() {
        throw new AssertionError("No instances of " + ConfigRows.class.getName());
    }

    /**
     * Builds the visible rows.
     *
     * <p>A module is kept when the query matches its name or its description —
     * the text on screen, not its id, so searching finds what a player can see.
     * A category with nothing left to show is dropped entirely rather than
     * leaving an empty header behind.</p>
     *
     * @param modules   every registered module, in registration order
     * @param query     what the player typed; blank keeps everything
     * @param collapsed categories whose modules are currently folded away
     * @return the rows to draw, top to bottom
     */
    public static List<ConfigRow> build(List<ModuleText> modules, String query, Set<ModuleCategory> collapsed) {
        List<ConfigRow> rows = new ArrayList<>();
        for (ModuleCategory category : ModuleCategory.values()) {
            List<ModuleText> all = inCategory(modules, category);
            if (all.isEmpty()) {
                continue;
            }
            List<ModuleText> shown = all.stream().filter(matching(query)).toList();
            if (shown.isEmpty()) {
                continue;
            }
            boolean folded = collapsed != null && collapsed.contains(category);
            rows.add(new ConfigRow.CategoryRow(category, shown.size(), all.size(), folded));
            if (folded) {
                continue;
            }
            for (ModuleText module : shown) {
                rows.add(new ConfigRow.ModuleRow(module.id(), module.name(), module.description(), module.enabled()));
                for (SettingText setting : module.settings()) {
                    rows.add(new ConfigRow.SettingRow(module.id(), setting.setting(), setting.label(), setting.tooltip()));
                }
            }
        }
        return List.copyOf(rows);
    }

    /**
     * Stacks rows top to bottom, so a list can lay itself out by iterating once.
     *
     * <p>Here rather than in the screen because it is the arithmetic a scrolled
     * list gets wrong: an off-by-one in an offset shows up as a row that cannot
     * be clicked, and a test can see that without drawing anything.</p>
     *
     * @param rows       the rows, top to bottom
     * @param topPadding empty space above the first row
     * @return one {@link Placed} per row, in the same order
     */
    public static List<Placed> layout(List<ConfigRow> rows, int topPadding) {
        List<Placed> placed = new ArrayList<>(rows.size());
        int y = topPadding;
        for (ConfigRow row : rows) {
            placed.add(new Placed(row, y, row.height()));
            y += row.height();
        }
        return List.copyOf(placed);
    }

    /** @return how tall the given rows are together, in GUI units */
    public static int contentHeight(List<ConfigRow> rows) {
        int height = 0;
        for (ConfigRow row : rows) {
            height += row.height();
        }
        return height;
    }

    /**
     * Flips a category between folded and open.
     *
     * @param collapsed  the current set, which this call does not modify
     * @param category   the category to toggle
     * @param newCollapsed the state to move to
     * @return the new set, safe to keep
     */
    public static Set<ModuleCategory> withCollapsed(Set<ModuleCategory> collapsed, ModuleCategory category,
            boolean newCollapsed) {
        Set<ModuleCategory> next = collapsed == null
                ? java.util.EnumSet.noneOf(ModuleCategory.class)
                : java.util.EnumSet.copyOf(collapsed);
        if (newCollapsed) {
            next.add(category);
        } else {
            next.remove(category);
        }
        return next;
    }

    private static List<ModuleText> inCategory(List<ModuleText> modules, ModuleCategory category) {
        return modules.stream().filter(module -> module.category() == category).toList();
    }

    private static Predicate<ModuleText> matching(String query) {
        return module -> ModuleSearch.matches(query, module.name(), module.description());
    }
}
