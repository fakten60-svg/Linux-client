package wtf.woke.lite.testutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The shipped English text, for tests that guard the link between the keys the
 * code asks for and the keys the mod actually carries.
 *
 * <p>Shared rather than duplicated: a key with no translation is not an error
 * the game reports, it silently shows the raw key, so more than one test wants
 * to look at this file and they had better be looking at the same one.</p>
 */
public final class LanguageFile {

    /** Classpath location of the language file. */
    public static final String RESOURCE = "/assets/wokewtf-lite/lang/en_us.json";

    /**
     * Prefix the game itself forces on a keybind category's label.
     *
     * <p>{@code KeyBinding.Category.getLabel()} builds its key as
     * {@code key.category.<namespace>.<path>}, so that one entry cannot live
     * under the mod's own prefix however much it would be nicer if it did.</p>
     */
    public static final String KEYBIND_CATEGORY_PREFIX = "key.category.wokewtf-lite.";

    /** Everything the mod's own code asks for lives under this prefix. */
    public static final String MOD_PREFIX = "wokewtf.lite.";

    private LanguageFile() {
        throw new AssertionError("No instances of " + LanguageFile.class.getName());
    }

    /** @return the parsed language file */
    public static JsonObject load() {
        InputStream stream = LanguageFile.class.getResourceAsStream(RESOURCE);
        assertNotNull(stream, RESOURCE + " must be on the classpath");
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception cause) {
            throw new AssertionError(RESOURCE + " must be valid JSON", cause);
        }
    }

    /** Fails with the missing keys listed, rather than one assertion per key. */
    public static void assertTranslated(JsonObject language, List<String> keys) {
        List<String> missing = keys.stream().filter(key -> !language.has(key)).toList();
        assertEquals(List.of(), missing, "no English text for: " + missing);
    }
}
