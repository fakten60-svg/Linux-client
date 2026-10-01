package wtf.woke.lite.macro;

import java.util.Objects;
import java.util.Optional;
import wtf.woke.lite.core.Validator;

/**
 * One saved chat line, waiting behind a keybind.
 *
 * <p>The text is <em>typed into the chat box</em>, never sent: pressing the key
 * opens chat with the line already in it and leaves the decision to press Enter
 * to the player. That is also why a macro cannot trip a server's flood
 * protection — nothing about a macro is sent by the mod, and the chat history
 * and send path are the game's own.</p>
 *
 * <p>The encoded form ({@code name|text}) is what the config stores. It is
 * split on the <em>first</em> separator, so a line may contain as many
 * separators as it likes and only the name is restricted. No Minecraft types,
 * so it is unit-testable without a game.</p>
 *
 * @param name the label the player gave the macro, used by the commands
 * @param text the line that is typed into chat
 */
public record ChatMacro(String name, String text) {

    /** Separator between the name and the text in the encoded form. */
    public static final char SEPARATOR = '|';

    /** Longest accepted name, so one entry cannot flood the config. */
    public static final int MAX_NAME_LENGTH = 32;

    /** Longest accepted chat line. */
    public static final int MAX_TEXT_LENGTH = 256;

    public ChatMacro {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(text, "text");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("invalid macro name: '" + name + "'");
        }
        if (!isValidText(text)) {
            throw new IllegalArgumentException("invalid macro text of " + text.length() + " characters");
        }
    }

    /** Encodes this macro into its single-line config form. */
    public String encode() {
        return name + SEPARATOR + text;
    }

    /**
     * @param entry one encoded macro
     * @return the decoded macro, or empty when the entry is not one — a
     *         hand-edited or older entry must never break startup
     */
    public static Optional<ChatMacro> decode(String entry) {
        if (entry == null) {
            return Optional.empty();
        }
        int split = entry.indexOf(SEPARATOR);
        if (split < 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ChatMacro(entry.substring(0, split), entry.substring(split + 1)));
        } catch (IllegalArgumentException notAMacro) {
            return Optional.empty();
        }
    }

    /**
     * @param name the candidate name
     * @return whether the name can be stored and encoded again unchanged
     */
    public static boolean isValidName(String name) {
        return name != null
                && !name.isBlank()
                && name.length() <= MAX_NAME_LENGTH
                && name.indexOf(SEPARATOR) < 0
                && Validator.isGameSafeText(name);
    }

    /**
     * @param text the candidate chat line
     * @return whether the line is short enough, not blank, and only carries
     *         characters the game itself would accept in chat
     */
    public static boolean isValidText(String text) {
        return text != null
                && !text.isBlank()
                && text.length() <= MAX_TEXT_LENGTH
                && Validator.isGameSafeText(text);
    }
}
