package wtf.woke.lite.inventory;

import java.util.Objects;

/**
 * What one slot holds, reduced to what reordering needs: a name, how many
 * items, and how many fit into one stack.
 *
 * <p>The name is the slot's identity as far as the sorter is concerned. Two
 * slots with the same name and a stack limit above one are the pair vanilla
 * would combine instead of swapping, so the sorter treats them as
 * interchangeable and never lets them trade places; two slots whose names differ
 * are always swapped, and the game agrees.</p>
 *
 * <p>An empty slot is {@link #EMPTY}, whose name is empty — so a slot is either
 * empty or holds at least one named item, never something in between.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 *
 * @param name     item name, empty only for {@link #EMPTY}
 * @param count    how many items are in the slot, zero only for {@link #EMPTY}
 * @param maxCount how many items fit into one stack, one for unstackable items
 */
public record SlotStack(String name, int count, int maxCount) {

    /** The content of a slot that holds nothing. */
    public static final SlotStack EMPTY = new SlotStack("", 0, 1);

    public SlotStack {
        Objects.requireNonNull(name, "name");
        if (maxCount < 1) {
            throw new IllegalArgumentException("maxCount must be >= 1, got " + maxCount);
        }
        if (count < 0 || count > maxCount) {
            throw new IllegalArgumentException("count must be in [0, " + maxCount + "], got " + count);
        }
        if (count == 0 && !name.isEmpty()) {
            throw new IllegalArgumentException("an empty slot has no name");
        }
        if (count > 0 && name.isEmpty()) {
            throw new IllegalArgumentException("a stack of items needs a name");
        }
    }

    /** @return {@code true} when the slot holds nothing */
    public boolean isEmpty() {
        return count == 0;
    }

    /**
     * @param other the content a click might bring in
     * @return whether vanilla would combine {@code other} into this slot instead
     *         of swapping the two; that needs the same name and room to stack
     */
    public boolean mergesWith(SlotStack other) {
        return maxCount > 1 && other.maxCount > 1 && count > 0 && other.count > 0 && name.equals(other.name);
    }

    /** @return how many more items this slot can take */
    public int freeSpace() {
        return maxCount - count;
    }

    /**
     * @param newCount the new item count
     * @return this content with that count, or {@link #EMPTY} for zero
     */
    public SlotStack withCount(int newCount) {
        return newCount == 0 ? EMPTY : new SlotStack(name, newCount, maxCount);
    }

    /** @return the ordering key of this content, {@link StackKey#EMPTY} when empty */
    public StackKey key() {
        return count == 0 ? StackKey.EMPTY : new StackKey(name, count);
    }
}
