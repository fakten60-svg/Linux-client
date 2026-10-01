package wtf.woke.lite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.testutil.LanguageFile;

/**
 * Guards the one-time chat guide printed after the first world join.
 *
 * <p>The line is built from a single translation key, so the default language
 * must carry it or a fresh client would show the raw key instead of the guide.
 * The German file ships the same key as the one non-default locale, and a
 * missing entry there is fine (the game falls back per key) but an English
 * placeholder left in its place is not — hence the inequality check.</p>
 *
 * <p>The exact wording is not pinned: only that English is present and that the
 * German entry, where it exists, is actually a different, non-empty string.</p>
 */
class WokeLiteFirstJoinGuideLanguageTest {

    /** The key the chat guide is translated from; see ClientEvents. */
    private static final String KEY = "wokewtf.lite.first_join.chat_guide";

    @Test
    void theChatGuideHasEnglishTextByDefault() {
        JsonObject english = LanguageFile.load();
        assertTrue(english.has(KEY), "en_us.json must carry the startup chat guide: " + KEY);
        assertFalse(english.get(KEY).getAsString().isBlank(),
                "the startup chat guide must not be blank");
    }

    @Test
    void theGermanTranslationIsPresentAndGerman() {
        JsonObject german = LanguageFile.load(LanguageFile.GERMAN_RESOURCE);
        assertTrue(german.has(KEY), "de_de.json must carry the startup chat guide: " + KEY);
        assertFalse(german.get(KEY).getAsString().isBlank(),
                "the German startup chat guide must not be blank");
        assertFalse(german.get(KEY).getAsString().equals(LanguageFile.load().get(KEY).getAsString()),
                "the German startup chat guide must not be an English placeholder");
    }
}
