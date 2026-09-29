package wtf.woke.lite.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns slot contents into the clicks that bring them into a wanted order.
 *
 * <p>The plan is a selection sort over whole stacks, and it is deliberately
 * built so that no click ever pairs two slots with the same name: those are the
 * only clicks the game could answer with a merge instead of a swap. Every stack
 * therefore keeps its identity and its size, and the inventory the plan is
 * replayed on ends up with exactly the stacks it started with, only in a
 * different order. Sorting never combines, splits or drops anything.</p>
 *
 * <p>Because the plan only contains positions, the caller decides which real
 * slots those positions mean; {@link SlotInteraction} is what checks the plan
 * against the click rules.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 */
public final class InventorySorter {

    /** Clicks one exchange is made of: take the first stack, then put it back. */
    public static final int CLICKS_PER_SWAP = 3;

    private InventorySorter() {
        throw new AssertionError("No instances of " + InventorySorter.class.getName());
    }

    /**
     * @param contents the slots, in the order they should end up in
     * @param order    which order to sort into
     * @return the slot names in the order they belong in; empty slots last
     */
    public static List<String> desiredNames(List<SlotStack> contents, SortOrder order) {
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(order, "order");

        Map<String, Integer> occurrences = new LinkedHashMap<>();
        Map<String, StackKey> representatives = new LinkedHashMap<>();
        for (SlotStack slot : contents) {
            if (slot.isEmpty()) {
                continue;
            }
            occurrences.merge(slot.name(), 1, Integer::sum);
            representatives.merge(slot.name(), slot.key(), InventorySorter::larger);
        }

        List<String> names = new ArrayList<>(representatives.keySet());
        names.sort(Comparator.comparing(representatives::get, order.comparator()));

        List<String> desired = new ArrayList<>(contents.size());
        for (String name : names) {
            desired.addAll(Collections.nCopies(occurrences.get(name), name));
        }
        while (desired.size() < contents.size()) {
            desired.add(SlotStack.EMPTY.name());
        }
        return List.copyOf(desired);
    }

    /**
     * @param contents the slots to sort
     * @param order    which order to sort into
     * @return the slot positions to click, in order; empty when already sorted
     */
    public static List<Integer> plan(List<SlotStack> contents, SortOrder order) {
        List<String> desired = desiredNames(contents, order);
        List<SlotStack> working = new ArrayList<>(contents);
        List<Integer> clicks = new ArrayList<>();

        for (int position = 0; position < working.size(); position++) {
            String wanted = desired.get(position);
            if (working.get(position).name().equals(wanted)) {
                continue;
            }
            int source = indexOfName(working, position + 1, wanted);
            if (source < 0) {
                // Placing names one by one cannot leave a wanted name missing;
                // if it ever did, clicking nothing beats clicking blind.
                return List.of();
            }
            // Pick up the occupant, take the wanted stack, put the rest back:
            // the two slots trade contents, whatever they hold.
            clicks.add(position);
            clicks.add(source);
            clicks.add(position);
            SlotStack displaced = working.get(position);
            working.set(position, working.get(source));
            working.set(source, displaced);
        }
        return List.copyOf(clicks);
    }

    private static int indexOfName(List<SlotStack> slots, int from, String name) {
        for (int index = from; index < slots.size(); index++) {
            if (slots.get(index).name().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    private static StackKey larger(StackKey first, StackKey second) {
        return first.count() >= second.count() ? first : second;
    }
}
