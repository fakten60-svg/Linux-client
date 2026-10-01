package wtf.woke.lite.core;

import java.util.List;
import java.util.Objects;

/**
 * Normalises a candidate value before it is stored in a {@link Setting}.
 *
 * <p>An implementation must never invent a value out of thin air: returning
 * {@code null} means "this candidate is unacceptable", and the setting falls
 * back to its own default. That keeps the rejection rule in one place and
 * keeps {@link Setting#set} total.</p>
 *
 * @param <T> the setting value type
 */
@FunctionalInterface
public interface Validator<T> {

    /**
     * @param candidate the value the caller asked for; may be {@code null}
     * @return the value to store, or {@code null} to reject the candidate
     */
    T sanitize(T candidate);

    /**
     * The game's own rule for text it will render or accept: no section sign
     * (which starts a formatting code), no control character, no DEL.
     *
     * <p>This mirrors vanilla's {@code SharedConstants.isValidChar}, and is why
     * user-supplied names and chat lines are checked against it: a name we
     * happily store can otherwise smuggle in a newline or a colour code that
     * the game itself would have refused at the keyboard, and it then renders
     * as broken HUD text or spoofed formatting.</p>
     *
     * @param text the candidate text; may be {@code null}
     * @return whether every character is one the game itself accepts
     */
    static boolean isGameSafeText(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 0xA7 is '§', 0x7F is DEL; everything below 0x20 is a control char.
            if (c == 0xA7 || c < 0x20 || c == 0x7F) {
                return false;
            }
        }
        return true;
    }

    /** Clamps an integer into {@code [min, max]}; {@code null} stays rejected. */
    static Validator<Integer> clampInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " > max " + max);
        }
        return candidate -> candidate == null ? null : Math.max(min, Math.min(max, candidate));
    }

    /** Clamps a decimal into {@code [min, max]}; non-finite values are rejected. */
    static Validator<Double> clampDouble(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " > max " + max);
        }
        return candidate -> {
            if (candidate == null || !Double.isFinite(candidate)) {
                return null;
            }
            return Math.max(min, Math.min(max, candidate));
        };
    }

    /** Truncates text to {@code maxLength} characters. */
    static Validator<String> limitLength(int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength must be >= 0");
        }
        return candidate -> candidate == null ? null : candidate.substring(0, Math.min(maxLength, candidate.length()));
    }

    /** Copies a string list, dropping blanks and bounding the entry count and length. */
    static Validator<List<String>> limitList(int maxEntries, int maxLength) {
        if (maxEntries < 0 || maxLength < 0) {
            throw new IllegalArgumentException("maxEntries and maxLength must be >= 0");
        }
        return candidate -> {
            if (candidate == null) {
                return null;
            }
            return candidate.stream()
                    .filter(Objects::nonNull)
                    .map(entry -> entry.substring(0, Math.min(maxLength, entry.length())))
                    .filter(entry -> !entry.isBlank())
                    .limit(maxEntries)
                    .toList();
        };
    }
}
