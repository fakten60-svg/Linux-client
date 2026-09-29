package wtf.woke.lite.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShareLinkTest {

    @TempDir
    Path directory;

    private String link(String name) {
        return ShareLink.fileUri(directory.resolve(name));
    }

    @Test
    void aPlainPathBecomesAFileUri() {
        String link = link("2026-01-01_10-00-00.png");

        assertTrue(link.startsWith("file:///"), "absolute links need the third slash: " + link);
        assertTrue(link.endsWith("/2026-01-01_10-00-00.png"), link);
        assertEquals(link, ShareLink.fileUri(directory.resolve("2026-01-01_10-00-00.png")));
    }

    @Test
    void charactersAUriCannotHoldAreEncoded() {
        assertTrue(link("my shot.png").endsWith("my%20shot.png"), "spaces are the common case");
        assertTrue(link("a#b.png").endsWith("a%23b.png"), "a raw # would cut the link in half");
        assertTrue(link("a?b.png").endsWith("a%3Fb.png"), "a raw ? would start a query");
        assertTrue(link("50%.png").endsWith("50%25.png"), "a raw % is not an escape");
        assertTrue(link("a[b].png").endsWith("a%5Bb%5D.png"), "brackets are not legal in a path");
    }

    @Test
    void ordinaryPathCharactersAreLeftAlone() {
        // Over-encoding would still work, but it makes links harder to read and
        // to compare, so pin what a URI actually allows in a path.
        assertTrue(link("a&b.png").endsWith("a&b.png"), link("a&b.png"));
        assertTrue(link("a+b,c;d=e.png").endsWith("a+b,c;d=e.png"), link("a+b,c;d=e.png"));
        assertTrue(link("a.b-c_d~e.png").endsWith("a.b-c_d~e.png"));
    }

    @Test
    void theLinkNeverHoldsACharacterThatWouldEndThePath() {
        String link = link("odd #name?&+.png");

        assertFalse(link.substring("file:///".length()).contains(" "), link);
        assertFalse(link.substring("file:///".length()).contains("#"), link);
        assertFalse(link.substring("file:///".length()).contains("?"), link);
    }

    @Test
    void nonAsciiIsEncodedAsUtf8() {
        // Encoded through a string rather than a file name: whether a Path may
        // even hold an umlaut depends on the JVM's file-name charset, while the
        // rule being pinned here does not.
        String path = "/\u00fcbersicht.png";

        assertEquals("/%C3%BCbersicht.png", ShareLink.encodePath(path));
        assertEquals("/%E6%97%A5%E6%9C%AC.png", ShareLink.encodePath("/\u65e5\u672c.png"));
    }

    @Test
    void aPercentIsAlwaysEncoded() {
        assertEquals("/50%25.png", ShareLink.encodePath("/50%.png"));
        assertEquals("/%2520.png", ShareLink.encodePath("/%20.png"), "an escape written by hand is text too");
    }

    @Test
    void thePathIsMadeAbsoluteAndFreeOfDotSegments() {
        Path winding = directory.resolve("sub").resolve("..").resolve(".").resolve("shot.png");

        String link = ShareLink.fileUri(winding);

        assertFalse(link.contains(".."), link);
        assertEquals(link("shot.png"), link);
    }

    @Test
    void aRelativePathIsResolvedAgainstTheWorkingDirectory() {
        String link = ShareLink.fileUri(Path.of("shot.png"));

        assertTrue(link.startsWith("file:///"), link);
        assertTrue(link.endsWith("/shot.png"), link);
    }
}
