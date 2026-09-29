package wtf.woke.lite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.testutil.FakeModule;

/**
 * The bridging half of {@link Setting}: what the config layer writes and reads
 * has to survive every supported type, and hostile input must never corrupt a
 * value.
 */
class SettingRawTest {

    @Test
    void rawRoundTripCoversEveryType() {
        Setting<Boolean> flag = Settings.bool("flag", false);
        flag.set(true);
        assertEquals(Boolean.TRUE, flag.toRaw());

        Setting<Integer> count = Settings.integer("count", 1, 0, 10);
        count.set(5);
        assertEquals(5, ((Number) count.toRaw()).intValue());

        Setting<Double> ratio = Settings.decimal("ratio", 1.0, 0.0, 2.0);
        ratio.set(1.5);
        assertEquals(1.5, ((Number) ratio.toRaw()).doubleValue());

        Setting<String> label = Settings.text("label", "a", 10);
        label.set("b");
        assertEquals("b", label.toRaw());

        Setting<FakeModule.Anchor> anchor = Settings.choice("anchor", FakeModule.Anchor.CENTER);
        assertEquals("CENTER", anchor.toRaw());

        Setting<Integer> colour = Settings.color("tint", 0xFFFFFFFF);
        colour.set(-16777216);
        assertEquals(-16777216, ((Number) colour.toRaw()).intValue());

        Setting<List<String>> list = Settings.stringList("macros", List.of(), 3, 5);
        list.set(List.of("x", "y"));
        assertEquals(List.of("x", "y"), list.toRaw());
    }

    @Test
    void fromRawIgnoresWrongTypesInsteadOfGuessing() {
        Setting<Integer> number = Settings.integer("offsetX", 4, 0, 100);
        assertFalse(number.fromRaw("not a number"));
        assertFalse(number.fromRaw(true));
        assertEquals(4, number.get().intValue());

        Setting<Boolean> flag = Settings.bool("visible", true);
        assertFalse(flag.fromRaw(1));
        assertTrue(flag.get());

        Setting<String> label = Settings.text("label", "hello", 16);
        assertFalse(label.fromRaw(42));
        assertEquals("hello", label.get());
    }

    @Test
    void fromRawAppliesCoercedValues() {
        Setting<Integer> number = Settings.integer("offsetX", 4, 0, 100);
        assertTrue(number.fromRaw(12.7));
        assertEquals(12, number.get().intValue(), "JSON numbers arrive as Number and are narrowed");

        // An integral JSON number widens into a decimal setting. The default is
        // deliberately not 1.0 so the widened write is observable: fromRaw
        // reports "no change" when the value already matches.
        Setting<Double> ratio = Settings.decimal("ratio", 0.5, 0.0, 2.0);
        assertTrue(ratio.fromRaw(1));
        assertEquals(1.0, ratio.get().doubleValue());
        assertFalse(ratio.fromRaw(1.0), "re-applying the same value is not a change");

        Setting<List<String>> list = Settings.stringList("macros", List.of(), 3, 5);
        assertTrue(list.fromRaw(List.of("ab", 7, "cdefgh")));
        assertEquals(List.of("ab", "7", "cdefg"), list.get());
    }

    @Test
    void enumDecodingIsCaseInsensitiveAndIgnoresUnknownNames() {
        Setting<FakeModule.Anchor> anchor = Settings.choice("anchor", FakeModule.Anchor.TOP_LEFT);
        assertTrue(anchor.fromRaw("bottom_right"));
        assertEquals(FakeModule.Anchor.BOTTOM_RIGHT, anchor.get());
        assertFalse(anchor.fromRaw("SIDEWAYS"));
        assertEquals(FakeModule.Anchor.BOTTOM_RIGHT, anchor.get());
    }

    @Test
    void nullRawValuesAreIgnored() {
        Setting<Boolean> flag = Settings.bool("visible", true);
        assertFalse(flag.fromRaw(null));
        assertTrue(flag.get());
    }
}
