package wtf.woke.lite;

import java.util.List;
import org.junit.jupiter.api.Test;
import wtf.woke.lite.testutil.LanguageFile;

/**
 * Guards the text the config screen asks for.
 *
 * <p>A key with no translation does not fail loudly in game: the screen shows the
 * raw key, which looks like a rendering bug rather than a missing string. The
 * keys asserted here are the ones the screen names directly; the per-setting
 * labels come from the settings themselves and are guarded where those live, and
 * the category labels by the enum test next door.</p>
 */
class WokeLiteConfigLanguageTest {

    @Test
    void everyStringTheConfigScreenNamesHasEnglishText() {
        LanguageFile.assertTranslated(LanguageFile.load(), List.of(
                "wokewtf.lite.config.title",
                "wokewtf.lite.config.search",
                "wokewtf.lite.config.search.tooltip",
                "wokewtf.lite.config.empty",
                "wokewtf.lite.config.reset",
                "wokewtf.lite.config.reset.tooltip",
                "wokewtf.lite.config.global.description",
                "wokewtf.lite.keybind.open_config"));
    }
}
