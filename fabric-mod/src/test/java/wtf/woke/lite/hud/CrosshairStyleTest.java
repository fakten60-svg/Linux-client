package wtf.woke.lite.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CrosshairStyleTest {

    @Test
    void translationKeysAreLowercaseUnderASharedPrefix() {
        assertEquals("wokewtf.lite.crosshair_style.cross", CrosshairStyle.CROSS.translationKey());
        assertEquals("wokewtf.lite.crosshair_style.dot", CrosshairStyle.DOT.translationKey());
        assertEquals("wokewtf.lite.crosshair_style.hidden", CrosshairStyle.HIDDEN.translationKey());
    }

    @Test
    void everyConstantHasADistinctKey() {
        long distinct = java.util.Arrays.stream(CrosshairStyle.values())
                .map(CrosshairStyle::translationKey)
                .distinct()
                .count();
        assertEquals(CrosshairStyle.values().length, distinct,
                "two constants sharing a label key would show the same name in the config screen");
    }
}
