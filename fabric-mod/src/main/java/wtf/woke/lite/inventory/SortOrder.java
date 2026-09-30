package wtf.woke.lite.inventory;

import java.util.Comparator;
import java.util.Locale;
import wtf.woke.lite.core.Labelled;

/**
 * How the sorted inventory is ordered.
 *
 * <p>Empty slots are never part of this comparison: the sorter packs the named
 * stacks to the front and leaves the empty slots behind them, whatever order is
 * chosen here.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 */
public enum SortOrder implements Labelled {

    /** Alphabetical by display name. */
    NAME,

    /** Biggest stacks first, then alphabetically. */
    COUNT;

    /** @return the comparison that puts one named stack before another */
    public Comparator<StackKey> comparator() {
        return switch (this) {
            case NAME -> Comparator.naturalOrder();
            case COUNT -> Comparator.comparingInt(StackKey::count).reversed().thenComparing(StackKey::name);
        };
    }

    /** @return the translation key for this constant's display label */
    public String translationKey() {
        return "wokewtf.lite.sort_order." + name().toLowerCase(Locale.ROOT);
    }
}
