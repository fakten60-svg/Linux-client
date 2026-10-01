package wtf.woke.lite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards {@code scripts/parse_session_log.sh} against the two ways it could be
 * quietly useless.
 *
 * <p>One logger, two spellings: {@code latest.log} shortens the name to
 * {@code (wtf Lite)} while {@code debug.log} keeps {@code (woke.wtf Lite)}, so a
 * pattern that matches only one makes half the logs look like the mod never
 * spoke. And the module list must be read out of the log rather than written
 * out a second time here, or a module added later would silently escape the
 * summary.</p>
 */
class WokeLiteSessionLogParserTest {

    private static final Path SCRIPT = Path.of("scripts", "parse_session_log.sh");

    private static String script() throws IOException {
        assertTrue(Files.isRegularFile(SCRIPT), "expected " + SCRIPT.toAbsolutePath() + " (run from fabric-mod)");
        return Files.readString(SCRIPT, StandardCharsets.UTF_8);
    }

    /** @return the tag regex the script declares, read as a Java pattern */
    private static Pattern tagPattern(String script) {
        Matcher declared = Pattern.compile("TAG_PATTERN='([^']+)'").matcher(script);
        assertTrue(declared.find(), "the script must declare a TAG_PATTERN");
        return Pattern.compile(declared.group(1));
    }

    @Test
    void theTagPatternCatchesBothSpellingsOfTheLoggerName() throws IOException {
        Pattern tag = tagPattern(script());

        assertTrue(tag.matcher("[10:00] [Render thread/INFO] (wtf Lite) ready").find(),
                "latest.log shortens the logger name to (wtf Lite)");
        assertTrue(tag.matcher("[10:00] [Render thread/DEBUG] (woke.wtf Lite) watch").find(),
                "debug.log keeps the full name (woke.wtf Lite)");
    }

    @Test
    void theModuleListComesFromTheLogRatherThanASecondHardcodedList() throws IOException {
        String script = script();

        assertTrue(script.contains("Registered modules:"),
                "the module list must be read from the log's own startup line");
        assertFalse(script.contains("MODULES=("),
                "a second hardcoded list would drift away from what the build registers");
    }
}
