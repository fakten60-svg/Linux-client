package wtf.woke.lite.inventory;

import java.util.Objects;

/**
 * What one inventory slot holds, reduced to what sorting needs: a name and a
 * count.
 *
 * <p>Two stacks with equal keys are treated as interchangeable. For real items
 * that means two stacks of the same item and count may end up in either order —
 * no item is lost, only the order between them is unspecified, which is the
 * same freedom a player has when sorting by hand.</p>
 *
 * <p>An empty slot is {@link #EMPTY}; every other key has a count of at least
 * one, so "empty" is never confused with "one item of something unnamed".</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 *
 * @param name  display name of the item, empty only for {@link #EMPTY}
 * @param count how many items the slot holds, zero only for {@link #EMPTY}
 */
public record StackKey(String name, int count) implements Comparable<StackKey> {

    /** The key of a slot that holds nothing. */
    public static final StackKey EMPTY = new StackKey("", 0);

    public StackKey {
        Objects.requireNonNull(name, "name");
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0, got " + count);
        }
        if (count == 0 && !name.isEmpty()) {
            throw new IllegalArgumentException("an empty stack has no name");
        }
        if (count > 0 && name.isEmpty()) {
            throw new IllegalArgumentException("a stack of items needs a name");
        }
    }

    /** @return {@code true} when the slot holds nothing */
    public boolean isEmpty() {
        return count == 0;
    }

    /** Orders by name, then by count, so equal names keep a stable order. */
    @Override
    public int compareTo(StackKey other) {
        int byName = name.compareTo(other.name);
        return byName != 0 ? byName : Integer.compare(count, other.count);
    }
}
