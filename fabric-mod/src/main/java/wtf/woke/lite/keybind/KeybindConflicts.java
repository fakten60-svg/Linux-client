package wtf.woke.lite.keybind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Finds two bindings that want the same key.
 *
 * <p>Minecraft lets two bindings share a key without complaining, and both then
 * fire: the second action looks like it silently does nothing, or does the
 * wrong thing, with nothing anywhere saying why. Catching the pair is the only
 * way to say so out loud, and it is worth doing on every world join too, since
 * the player can rebind either side at any time.</p>
 *
 * <p>Keys are compared as the game's own translation keys
 * ({@code key.keyboard.g}), so this needs no game and no input backend: the
 * client only has to hand over the pairs it can already read from its own
 * bindings.</p>
 */
public final class KeybindConflicts {

    /** The translation key the game uses for "no key", which never conflicts. */
    public static final String UNBOUND = "key.keyboard.unknown";

    /**
     * One key claimed by more than one binding.
     *
     * @param key    the shared key's translation key
     * @param owners what claims it, in the order the pairs were supplied
     */
    public record Conflict(String key, List<String> owners) {

        public Conflict {
            Objects.requireNonNull(key, "key");
            owners = List.copyOf(owners);
        }

        /** @return how many bindings share the key, always at least two */
        public int ownerCount() {
            return owners.size();
        }
    }

    private KeybindConflicts() {
        throw new AssertionError("No instances of " + KeybindConflicts.class.getName());
    }

    /**
     * @param ownerToKey binding owner to the key it is bound to, in the order
     *                   the caller wants conflicts reported
     * @return every key claimed by two or more owners; unbound entries are
     *         ignored, since the game does not fire them at all
     */
    public static List<Conflict> find(Map<String, String> ownerToKey) {
        Objects.requireNonNull(ownerToKey, "ownerToKey");
        Map<String, List<String>> byKey = new LinkedHashMap<>();
        ownerToKey.forEach((owner, key) -> {
            if (owner == null || key == null || key.isBlank() || UNBOUND.equals(key)) {
                return;
            }
            byKey.computeIfAbsent(key, ignored -> new ArrayList<>(2)).add(owner);
        });
        List<Conflict> conflicts = new ArrayList<>();
        byKey.forEach((key, owners) -> {
            if (owners.size() > 1) {
                conflicts.add(new Conflict(key, owners));
            }
        });
        return List.copyOf(conflicts);
    }

    /**
     * Like {@link #find(Map)}, but keeps only the conflicts that matter to the
     * caller: those where at least one owner is named in {@code ours}.
     *
     * <p>Vanilla ships several bindings that deliberately share a key (the debug
     * keys sit on the movement keys, for one), and the game fires all of them
     * without a word. Reporting those pairs would bury the one line that is
     * actually actionable, so a pair nobody in {@code ours} is part of is
     * dropped here instead of being filtered at the call site.</p>
     *
     * @param ownerToKey binding owner to the key it is bound to
     * @param ours       owners whose conflicts are worth reporting
     * @return the conflicts with at least one owner in {@code ours}
     */
    public static List<Conflict> involving(Map<String, String> ownerToKey, Set<String> ours) {
        Objects.requireNonNull(ownerToKey, "ownerToKey");
        Objects.requireNonNull(ours, "ours");
        if (ours.isEmpty()) {
            return List.of();
        }
        List<Conflict> kept = new ArrayList<>();
        for (Conflict conflict : find(ownerToKey)) {
            for (String owner : conflict.owners()) {
                if (ours.contains(owner)) {
                    kept.add(conflict);
                    break;
                }
            }
        }
        return List.copyOf(kept);
    }
}
