package wtf.woke.lite.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.search.ModuleSearch.Target;

/**
 * Pins what a search box is allowed to be fussy about. A player typing quickly
 * on a foreign keyboard should not be told "no results" because of a capital
 * letter, an accent or a diaeresis.
 */
class ModuleSearchTest {

    private static final Target CROSSHAIR = new Target("ui.crosshair", "Crosshair", "Draws your own crosshair.");
    private static final Target AUTO_RECONNECT = new Target("util.auto_reconnect", "Auto Reconnect",
            "Reconnects after a dropped connection.");
    private static final Target GROESSE = new Target("demo.groesse", "Gr\u00f6\u00dfe",
            "Zeigt die Gr\u00f6\u00dfe des Blockes an.");
    private static final List<Target> ALL = List.of(CROSSHAIR, AUTO_RECONNECT, GROESSE);

    @Test
    void aBlankQueryMatchesEverything() {
        assertTrue(ModuleSearch.matches("", "Crosshair", "Draws your own crosshair."));
        assertTrue(ModuleSearch.matches("   ", "Crosshair", "Draws your own crosshair."));
        assertTrue(ModuleSearch.matches(null, "Crosshair", "Draws your own crosshair."));
        assertEquals(ALL, ModuleSearch.filter(ALL, ""));
        assertEquals(ALL, ModuleSearch.filter(ALL, null));
    }

    @Test
    void anExactNameMatches() {
        assertTrue(ModuleSearch.matches("Crosshair", CROSSHAIR));
        assertEquals(List.of(CROSSHAIR), ModuleSearch.filter(ALL, "Crosshair"));
    }

    @Test
    void aPrefixMatches() {
        assertTrue(ModuleSearch.matches("Cross", CROSSHAIR));
        assertTrue(ModuleSearch.matches("Auto Re", AUTO_RECONNECT));
        assertEquals(List.of(AUTO_RECONNECT), ModuleSearch.filter(ALL, "Auto Rec"));
    }

    @Test
    void aSubstringOfTheDescriptionMatches() {
        assertTrue(ModuleSearch.matches("dropped", AUTO_RECONNECT));
        assertTrue(ModuleSearch.matches("clipboard", "Screenshots", "Puts a path on the clipboard."));
        assertEquals(List.of(AUTO_RECONNECT), ModuleSearch.filter(ALL, "dropped"));
    }

    @Test
    void matchingIgnoresCase() {
        assertTrue(ModuleSearch.matches("crosshair", CROSSHAIR));
        assertTrue(ModuleSearch.matches("CROSSHAIR", CROSSHAIR));
        assertTrue(ModuleSearch.matches("cRoSsHaIr", CROSSHAIR));
        assertTrue(ModuleSearch.matches("DROPPED", AUTO_RECONNECT));
    }

    @Test
    void matchingIgnoresUmlautCase() {
        // Lowercasing must happen with a fixed locale: with a Turkish default
        // locale, "I" lowercases to a dotless "\u0131" and this would fail.
        assertTrue(ModuleSearch.matches("\u00f6", GROESSE));          // "ö"
        assertTrue(ModuleSearch.matches("\u00d6", GROESSE));          // "Ö"
        assertTrue(ModuleSearch.matches("GR\u00d6\u00dfE", GROESSE)); // "GRÖSSE"
        assertTrue(ModuleSearch.matches("gr\u00f6\u00dfe", GROESSE)); // "größe"
    }

    @Test
    void accentsAndSharpSAreFoldedAway() {
        assertTrue(ModuleSearch.matches("grosse", GROESSE));
        assertTrue(ModuleSearch.matches("grosse", "Gr\u00f6\u00dfe", ""));
        assertTrue(ModuleSearch.matches("grösse", "grosse", ""));
        assertTrue(ModuleSearch.matches("uber", "\u00dcber", ""));
        assertTrue(ModuleSearch.matches("\u00fcber", "uber", ""));
    }

    @Test
    void aQueryThatMatchesNothingReturnsNothing() {
        assertFalse(ModuleSearch.matches("zzzz", CROSSHAIR));
        assertEquals(List.of(), ModuleSearch.filter(ALL, "zzzz"));
    }

    @Test
    void filteringKeepsTheOriginalOrder() {
        // "o" appears in all three names, so the result is the input order.
        assertEquals(ALL, ModuleSearch.filter(ALL, "o"));
        assertEquals(List.of(AUTO_RECONNECT), ModuleSearch.filter(ALL, "reconnect"));
    }

    @Test
    void targetsTolerateMissingText() {
        Target bare = new Target("x", null, null);

        assertTrue(ModuleSearch.matches("", bare));
        assertFalse(ModuleSearch.matches("anything", bare));
        assertEquals("", bare.name());
        assertEquals("", bare.description());
    }

    @Test
    void foldingTrimsAndLowercases() {
        assertEquals("crosshair", ModuleSearch.fold("  CrossHair  "));
        assertEquals("", ModuleSearch.fold(null));
        assertEquals("", ModuleSearch.fold("   "));
    }
}
