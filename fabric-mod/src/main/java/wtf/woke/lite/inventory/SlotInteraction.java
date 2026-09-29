package wtf.woke.lite.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A model of what one vanilla pickup click does to a slot and to the cursor.
 *
 * <p>This is the only place that knows the click rules, so a sort plan can be
 * built against them and then replayed against them again when it is checked.
 * The rules are the four cases the game has for a left click with no modifier:
 * an empty cursor takes the whole stack, an empty slot receives the cursor,
 * equal stackable content is combined (filling the slot, the rest staying on the
 * cursor), and anything else trades places with the cursor.</p>
 *
 * <p>Nothing here merges two slots that the game would swap, and nothing here
 * splits a stack, so a replayed plan can be checked against the inventory it
 * started from.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 */
public final class SlotInteraction {

    /** What one click did. */
    public enum Outcome {

        /** The click changed nothing, e.g. picking up from an empty slot. */
        NOTHING,

        /** The cursor took the slot's stack. */
        PICKED_UP,

        /** The cursor's stack went into the empty slot. */
        PUT_DOWN,

        /** The cursor's stack and the slot's stack traded places. */
        SWAPPED,

        /** The cursor's stack was combined into the slot, as far as it fitted. */
        MERGED
    }

    private final List<SlotStack> slots;
    private final List<Outcome> outcomes = new ArrayList<>();

    private SlotStack cursor = SlotStack.EMPTY;

    /**
     * @param initial the slot contents to work on; copied, never modified
     */
    public SlotInteraction(List<SlotStack> initial) {
        Objects.requireNonNull(initial, "initial");
        this.slots = new ArrayList<>(initial);
    }

    /**
     * @param initial the slot contents to start from
     * @param clicks  slot indices to click in order
     * @return the slot contents after those clicks
     */
    public static List<SlotStack> replay(List<SlotStack> initial, List<Integer> clicks) {
        Objects.requireNonNull(clicks, "clicks");
        SlotInteraction interaction = new SlotInteraction(initial);
        clicks.forEach(interaction::click);
        return interaction.snapshot();
    }

    /**
     * Applies one left click.
     *
     * @param index the slot to click
     * @return what the click did
     * @throws IndexOutOfBoundsException if there is no such slot
     */
    public Outcome click(int index) {
        if (index < 0 || index >= slots.size()) {
            throw new IndexOutOfBoundsException("slot " + index + " of " + slots.size());
        }
        Outcome outcome = apply(index);
        outcomes.add(outcome);
        return outcome;
    }

    /** @return the current slot contents */
    public List<SlotStack> snapshot() {
        return List.copyOf(slots);
    }

    /** @return what every click so far did, in order */
    public List<Outcome> outcomes() {
        return List.copyOf(outcomes);
    }

    /** @return what the cursor holds */
    public SlotStack cursor() {
        return cursor;
    }

    /** @return the number of slots */
    public int size() {
        return slots.size();
    }

    private Outcome apply(int index) {
        SlotStack target = slots.get(index);
        if (cursor.isEmpty()) {
            if (target.isEmpty()) {
                return Outcome.NOTHING;
            }
            cursor = target;
            slots.set(index, SlotStack.EMPTY);
            return Outcome.PICKED_UP;
        }
        if (target.isEmpty()) {
            slots.set(index, cursor);
            cursor = SlotStack.EMPTY;
            return Outcome.PUT_DOWN;
        }
        if (target.mergesWith(cursor)) {
            int moved = Math.min(cursor.count(), target.freeSpace());
            slots.set(index, target.withCount(target.count() + moved));
            cursor = cursor.withCount(cursor.count() - moved);
            return moved == 0 ? Outcome.NOTHING : Outcome.MERGED;
        }
        slots.set(index, cursor);
        cursor = target;
        return Outcome.SWAPPED;
    }
}
