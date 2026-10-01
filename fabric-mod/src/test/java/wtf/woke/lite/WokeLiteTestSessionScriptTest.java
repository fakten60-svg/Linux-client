package wtf.woke.lite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.testutil.LanguageFile;

/**
 * Guards that {@code scripts/test_session.sh} switches off every module the mod
 * ships.
 *
 * <p>The script is the baseline the whole manual session stands on: a module it
 * forgets starts the tests already on, and a test for that module then proves
 * nothing. The expected list is not written out here — it is derived from the
 * language file, where every module has exactly one name entry of the shape
 * {@code wokewtf.lite.module.<category>.<id>} — so adding a module without
 * adding it to the script fails here instead of in game.</p>
 */
class WokeLiteTestSessionScriptTest {

    /** Module name keys have exactly this many dot-separated parts. */
    private static final int NAME_KEY_PARTS = 5;
    private static final String MODULE_PREFIX = "wokewtf.lite.module.";
    private static final Path SCRIPT = Path.of("scripts", "test_session.sh");

    private static Set<String> moduleIdsFromLanguage() {
        Set<String> ids = new LinkedHashSet<>();
        for (String key : LanguageFile.load().keySet()) {
            if (!key.startsWith(MODULE_PREFIX)) {
                continue;
            }
            String[] parts = key.split("\\.");
            if (parts.length == NAME_KEY_PARTS) {
                ids.add(parts[3] + "." + parts[4]);
            }
        }
        return ids;
    }

    /** @return the entries of the script's {@code MODULES=( ... )} array */
    private static Set<String> moduleIdsFromScript(String script) {
        int start = script.indexOf("MODULES=(");
        assertTrue(start >= 0, "the script must declare a MODULES array");
        int end = script.indexOf(')', start);
        assertTrue(end > start, "the MODULES array must be closed");
        Set<String> ids = new LinkedHashSet<>();
        for (String line : script.substring(start + "MODULES=(".length(), end).split("\\R")) {
            String id = line.trim();
            if (!id.isEmpty() && !id.startsWith("#")) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Test
    void theSessionScriptTurnsOffEveryModuleTheModShips() throws IOException {
        assertTrue(Files.isRegularFile(SCRIPT), "expected " + SCRIPT.toAbsolutePath() + " (run from fabric-mod)");
        Set<String> expected = moduleIdsFromLanguage();

        Set<String> actual = moduleIdsFromScript(Files.readString(SCRIPT, StandardCharsets.UTF_8));

        assertEquals(13, expected.size(), "the mod ships thirteen modules; the language file says otherwise");
        assertEquals(expected, actual, "the session script must reset exactly the shipped modules");
    }

    @Test
    void theSessionScriptForcesThemOffRatherThanRelyingOnDefaults() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertTrue(script.contains("\"enabled\": false"),
                "every entry must be written as enabled:false, not left to the shipped default");
        assertTrue(script.contains("--reset-only"),
                "a reset-only mode is what makes the baseline verifiable without launching the game");
        List.of("hud.fps").forEach(id -> assertTrue(script.contains(id), "missing module: " + id));
    }
}
