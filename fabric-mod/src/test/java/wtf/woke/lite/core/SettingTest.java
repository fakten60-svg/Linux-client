package wtf.woke.lite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.testutil.FakeModule;

class SettingTest {

    @Test
    void booleanSettingStartsAtItsDefault() {
        Setting<Boolean> setting = Settings.bool("visible", true);
        assertTrue(setting.get());
        assertTrue(setting.isDefault());
        assertEquals(SettingType.BOOLEAN, setting.type());
    }

    @Test
    void setReportsWhetherTheValueChanged() {
        Setting<Boolean> setting = Settings.bool("visible", true);
        assertTrue(setting.set(false));
        assertFalse(setting.set(false), "setting the same value again is not a change");
        assertFalse(setting.isDefault());
        assertTrue(setting.reset());
        assertTrue(setting.isDefault());
    }

    @Test
    void integersAreClampedIntoRange() {
        Setting<Integer> setting = Settings.integer("offsetX", 4, 0, 10);
        setting.set(50);
        assertEquals(10, setting.get().intValue());
        setting.set(-5);
        assertEquals(0, setting.get().intValue());
    }

    @Test
    void decimalsAreClampedAndNonFiniteValuesFallBackToDefault() {
        Setting<Double> setting = Settings.decimal("scale", 1.0, 0.5, 3.0);
        setting.set(9.0);
        assertEquals(3.0, setting.get().doubleValue());
        setting.set(Double.NaN);
        assertEquals(1.0, setting.get().doubleValue());
        setting.set(Double.POSITIVE_INFINITY);
        assertEquals(1.0, setting.get().doubleValue());
    }

    @Test
    void textIsTruncatedToItsLimit() {
        Setting<String> setting = Settings.text("label", "hello", 4);
        setting.set("abcdef");
        assertEquals("abcd", setting.get());
    }

    @Test
    void nullAlwaysFallsBackToTheDefault() {
        Setting<String> setting = Settings.text("label", "hello", 16);
        setting.set("changed");
        assertTrue(setting.set(null));
        assertEquals("hello", setting.get());
    }

    @Test
    void blankKeybindsFallBackToTheDefault() {
        Setting<String> setting = Settings.keybind("toggle", "key.keyboard.g");
        setting.set("key.keyboard.k");
        assertEquals("key.keyboard.k", setting.get());
        setting.set("   ");
        assertEquals("key.keyboard.g", setting.get());
    }

    @Test
    void enumsAcceptAnyConstantAndRejectNull() {
        Setting<FakeModule.Anchor> setting = Settings.choice("anchor", FakeModule.Anchor.TOP_LEFT);
        setting.set(FakeModule.Anchor.BOTTOM_RIGHT);
        assertEquals(FakeModule.Anchor.BOTTOM_RIGHT, setting.get());
        setting.set(null);
        assertEquals(FakeModule.Anchor.TOP_LEFT, setting.get());
    }

    @Test
    void stringListsAreBoundedAndImmutable() {
        Setting<List<String>> setting = Settings.stringList("macros", List.of("a"), 2, 3);
        setting.set(List.of("one", "toolong", "three", "four"));
        assertEquals(List.of("one", "too"), setting.get(), "entries are truncated, blanks dropped, then counted");
        assertThrows(UnsupportedOperationException.class, () -> setting.get().add("nope"));
    }

    @Test
    void observersFireOncePerChangeAndSurviveAFailingPeer() {
        Setting<Integer> setting = Settings.integer("offsetX", 1, 0, 10);
        AtomicInteger seen = new AtomicInteger();
        setting.addListener(seen::incrementAndGet);
        setting.addListener(() -> {
            throw new IllegalStateException("planned observer failure");
        });
        AtomicInteger afterFailure = new AtomicInteger();
        setting.addListener(afterFailure::incrementAndGet);

        assertTrue(setting.set(2));
        assertEquals(1, seen.get());
        assertEquals(1, afterFailure.get(), "a throwing observer must not stop the others");
        assertFalse(setting.set(2));
        assertEquals(1, seen.get(), "unchanged values must not notify");
        assertEquals(2, setting.get().intValue(), "the value is stored before observers run");
    }

    @Test
    void changeConsumerSeesTheStoredValue() {
        Setting<Integer> setting = Settings.integer("offsetX", 1, 0, 10);
        List<Integer> seen = new ArrayList<>();
        setting.onChanged(seen::add);
        setting.set(7);
        setting.set(99);
        assertEquals(List.of(7, 10), seen);
    }

    @Test
    void describedByOverridesGeneratedKeys() {
        Setting<Boolean> flag = Settings.bool("visible", true).describedBy("custom.label", "custom.tip");
        assertEquals("custom.label", flag.translationKey());
        assertEquals("custom.tip", flag.descriptionKey().orElseThrow());
        assertTrue(Settings.bool("visible", true).descriptionKey().isEmpty());
    }

    @Test
    void identifiersAreValidatedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> Settings.bool("  ", true));
        assertThrows(IllegalArgumentException.class, () -> Settings.stringList("list", List.of(), -1, 1));
    }
}
