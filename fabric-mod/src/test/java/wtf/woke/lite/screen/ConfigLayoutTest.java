package wtf.woke.lite.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;

/**
 * Pins how the config screen's scrolling list stacks its rows.
 *
 * <p>The pane turns a pointer position into a row by subtracting a scroll offset
 * from it and comparing it against these numbers, so an offset that is off by one
 * is a row that cannot be clicked — a failure that is invisible in a screenshot
 * and obvious in a test.</p>
 */
class ConfigLayoutTest {

    private static final Setting<Boolean> TOGGLE = Settings.bool("demo", true);

    private static ConfigRows.ModuleText module(String id, ModuleCategory category) {
        return new ConfigRows.ModuleText(id, category, "Demo Module", "A demo module.", false,
                List.of(new ConfigRows.SettingText(TOGGLE, "Demo Setting", "A demo setting.")));
    }

    private static List<ConfigRow> oneModule() {
        return ConfigRows.build(List.of(module("hud.fps", ModuleCategory.HUD)), "", Set.of());
    }

    @Test
    void rowsStackWithoutGapsOrOverlaps() {
        List<ConfigRow> rows = oneModule();
        List<ConfigRows.Placed> placed = ConfigRows.layout(rows, 4);

        assertEquals(rows.size(), placed.size());
        int y = 4;
        for (int index = 0; index < placed.size(); index++) {
            assertEquals(rows.get(index), placed.get(index).row(), "order is preserved");
            assertEquals(y, placed.get(index).y(), "rows stack without gaps");
            assertEquals(rows.get(index).height(), placed.get(index).height());
            y += placed.get(index).height();
        }
        assertEquals(ConfigRows.contentHeight(rows) + 4, y, "the last row ends where the content does");
    }

    @Test
    void theTopPaddingMovesEveryRowDown() {
        List<ConfigRow> rows = oneModule();

        assertEquals(4, ConfigRows.layout(rows, 4).get(0).y());
        assertEquals(0, ConfigRows.layout(rows, 0).get(0).y());
        assertEquals(ConfigRows.layout(rows, 4).get(1).y(),
                ConfigRows.layout(rows, 4).get(0).y() + rows.get(0).height());
    }

    @Test
    void layingOutNothingGivesNothing() {
        assertEquals(List.of(), ConfigRows.layout(List.of(), 4));
        assertEquals(0, ConfigRows.contentHeight(List.of()));
    }
}
