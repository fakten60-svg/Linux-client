package wtf.woke.lite.screenshot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Turns a screenshot on disk into a link that can be pasted somewhere else.
 *
 * <p>The game's own chat message already opens a screenshot when clicked, but a
 * click is not text: it cannot be copied out of chat, so it is no use for
 * pasting a shot into a bug report, an editor or a chat program. A
 * {@code file://} URI is text, and this is where it is built properly.</p>
 *
 * <p>Properly matters, because a raw path is not a valid URI as soon as it holds
 * a space, a {@code #}, a {@code ?} or anything outside ASCII — the things a
 * screenshot folder on a real machine is full of. The encoding is done here
 * rather than by {@link java.net.URI}, because that constructor quotes those
 * ASCII cases but leaves non-ASCII characters raw, which is exactly the case a
 * player's language is most likely to produce.</p>
 *
 * <p>Only the path is used; nothing is read from the file system, and the link
 * points at a file on this machine and nowhere else.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 */
public final class ShareLink {

    /** Scheme of the links this builds. */
    public static final String SCHEME = "file://";

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private ShareLink() {
        throw new AssertionError("No instances of " + ShareLink.class.getName());
    }

    /**
     * @param file a screenshot
     * @return its {@code file://} link, with everything a URI cannot hold
     *         percent-encoded
     */
    public static String fileUri(Path file) {
        Objects.requireNonNull(file, "file");
        return SCHEME + encodePath(slashSeparated(file));
    }

    /**
     * Percent-encodes a path for use inside a URI.
     *
     * <p>Everything a URI path may hold is kept as it is, so ordinary links stay
     * readable; everything else, including the whole non-ASCII range, is written
     * as its UTF-8 bytes. A {@code %} is always escaped, since a raw one would
     * be read as the start of an escape.</p>
     *
     * @param path the path to encode
     * @return the encoded path
     */
    static String encodePath(String path) {
        StringBuilder encoded = new StringBuilder(path.length() + 16);
        for (byte value : path.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = value & 0xFF;
            if (isAllowedInPath(unsigned)) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%').append(HEX_DIGITS[unsigned >> 4]).append(HEX_DIGITS[unsigned & 0xF]);
            }
        }
        return encoded.toString();
    }

    /** @return whether a character may stand for itself in a URI path */
    private static boolean isAllowedInPath(int value) {
        boolean unreserved = value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9'
                || value == '-' || value == '.' || value == '_' || value == '~';
        boolean subDelimiter = value == '!' || value == '$' || value == '&' || value == '\''
                || value == '(' || value == ')' || value == '*' || value == '+' || value == ','
                || value == ';' || value == '=';
        return unreserved || subDelimiter || value == ':' || value == '@' || value == '/';
    }

    /** @return the path with forward slashes, as a URI needs, and a leading one */
    private static String slashSeparated(Path file) {
        String path = file.toAbsolutePath().normalize().toString().replace('\\', '/');
        return path.startsWith("/") ? path : "/" + path;
    }
}
