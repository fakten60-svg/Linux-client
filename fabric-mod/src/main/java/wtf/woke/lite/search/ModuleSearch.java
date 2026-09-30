package wtf.woke.lite.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Matches a typed query against a module's name and description.
 *
 * <p>Deliberately outside the client: the strings it compares are ordinary
 * strings, so the whole rule set — what counts as a match, how case is treated,
 * how accents and umlauts behave — can be checked without a game. The config
 * screen only supplies the strings its language file resolved.</p>
 *
 * <p>The query is folded before it is compared, and so is the text it is
 * compared against. Folding lowercases with {@link Locale#ROOT}, which is the
 * only way to get the same answer on a machine whose default locale disagrees
 * about what "I" lowercases to, and then strips accents, so typing
 * {@code grosse} finds <em>Größe</em> and {@code ss} finds <em>ß</em>. A player
 * reaching for a search box is usually typing quickly on a foreign keyboard;
 * refusing a match because of a diaeresis would be the wrong kind of correct.</p>
 */
public final class ModuleSearch {

    /**
     * One searchable module.
     *
     * @param id          the module's stable id, used for identity, never for matching
     * @param name        the module's translated name
     * @param description the module's translated description
     */
    public record Target(String id, String name, String description) {

        public Target {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
            description = description == null ? "" : description;
        }
    }

    private ModuleSearch() {
        throw new AssertionError("No instances of " + ModuleSearch.class.getName());
    }

    /**
     * @param query       what the player typed; blank matches everything
     * @param name        the module's name
     * @param description the module's description
     * @return whether the query matches either field
     */
    public static boolean matches(String query, String name, String description) {
        String needle = fold(query);
        if (needle.isEmpty()) {
            return true;
        }
        return fold(name).contains(needle) || fold(description).contains(needle);
    }

    /** @return whether the query matches the target's name or description */
    public static boolean matches(String query, Target target) {
        Objects.requireNonNull(target, "target");
        return matches(query, target.name(), target.description());
    }

    /**
     * @param targets the modules to filter, in the order they should stay in
     * @param query   what the player typed
     * @return the matching targets, in their original order
     */
    public static List<Target> filter(List<Target> targets, String query) {
        Objects.requireNonNull(targets, "targets");
        List<Target> matching = new ArrayList<>(targets.size());
        for (Target target : targets) {
            if (target != null && matches(query, target)) {
                matching.add(target);
            }
        }
        return List.copyOf(matching);
    }

    /**
     * Folds a string into the form comparisons use.
     *
     * @param value any string, including {@code null}
     * @return the lowercased, accent-free, trimmed form
     */
    public static String fold(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder folded = new StringBuilder(decomposed.length());
        for (int index = 0; index < decomposed.length(); index++) {
            char character = decomposed.charAt(index);
            switch (character) {
                case '\u00DF' -> folded.append("ss");   // ß
                case '\u00E6' -> folded.append("ae");   // æ
                case '\u00F8' -> folded.append("o");    // ø
                default -> {
                    // NFD splits "ä" into "a" plus a combining diaeresis, so
                    // dropping the marks is what removes the accent.
                    if (Character.getType(character) != Character.NON_SPACING_MARK) {
                        folded.append(character);
                    }
                }
            }
        }
        return folded.toString().trim();
    }
}
