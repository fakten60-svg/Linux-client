package wtf.woke.lite.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The contract the inventory sort module leans on: a plan is a reordering, and
 * replaying one never combines, splits or drops a stack.
 */
class InventorySorterTest {

    private static final List<String> ITEMS = List.of("Apple", "Bread", "Coal", "Dirt", "Emerald");

    private static SlotStack stack(String name, int count) {
        return new SlotStack(name, count, 64);
    }

    private static SlotStack tool(String name) {
        return new SlotStack(name, 1, 1);
    }

    private static List<SlotStack> contents(String... names) {
        List<SlotStack> slots = new ArrayList<>(names.length);
        for (String name : names) {
            slots.add(name.isEmpty() ? SlotStack.EMPTY : stack(name, 1));
        }
        return slots;
    }

    private static List<String> namesOf(List<SlotStack> slots) {
        return slots.stream().map(SlotStack::name).toList();
    }

    /** @return the stacks that are present, in an order that ignores where they sit */
    private static List<SlotStack> stacksOf(List<SlotStack> slots) {
        return slots.stream()
                .filter(slot -> !slot.isEmpty())
                .sorted(Comparator.comparing(SlotStack::key).thenComparingInt(SlotStack::maxCount))
                .toList();
    }

    @Test
    void theWantedOrderPutsNamesInOrderAndEmptySlotsLast() {
        List<SlotStack> inventory = contents("Dirt", "Apple", "", "Coal");

        assertEquals(List.of("Apple", "Coal", "Dirt", ""), InventorySorter.desiredNames(inventory, SortOrder.NAME));
    }

    @Test
    void countOrderRanksEachNameByItsBiggestStack() {
        List<SlotStack> inventory = List.of(stack("Dirt", 1), stack("Coal", 60), stack("Apple", 32), SlotStack.EMPTY);

        assertEquals(List.of("Coal", "Apple", "Dirt", ""),
                InventorySorter.desiredNames(inventory, SortOrder.COUNT));
    }

    @Test
    void stacksOfTheSameNameEndUpTogether() {
        List<SlotStack> inventory = List.of(stack("Dirt", 64), stack("Apple", 5), stack("Dirt", 20));

        assertEquals(List.of("Apple", "Dirt", "Dirt"), InventorySorter.desiredNames(inventory, SortOrder.NAME));
        assertEquals(List.of("Dirt", "Dirt", "Apple"), InventorySorter.desiredNames(inventory, SortOrder.COUNT),
                "the full stack carries the name forward");
    }

    @Test
    void anInventoryThatIsAlreadyInOrderNeedsNoClicks() {
        assertEquals(List.of(), InventorySorter.plan(contents("Apple", "Coal", "Dirt", ""), SortOrder.NAME));
        assertEquals(List.of(), InventorySorter.plan(List.of(SlotStack.EMPTY, SlotStack.EMPTY), SortOrder.NAME));
        assertEquals(List.of(), InventorySorter.plan(List.of(), SortOrder.NAME));
    }

    @Test
    void replayingAPlanProducesTheWantedOrder() {
        List<SlotStack> inventory = contents("Dirt", "Apple", "", "Coal", "", "Bread");
        List<SlotStack> sorted = SlotInteraction.replay(inventory, InventorySorter.plan(inventory, SortOrder.NAME));

        assertEquals(InventorySorter.desiredNames(inventory, SortOrder.NAME), namesOf(sorted));
        assertEquals(List.of("Apple", "Bread", "Coal", "Dirt", "", ""), namesOf(sorted));
    }

    @Test
    void aPlanNeverCombinesTwoStacks() {
        List<SlotStack> inventory = List.of(stack("Dirt", 40), stack("Dirt", 40), stack("Apple", 5));
        SlotInteraction replay = new SlotInteraction(inventory);

        InventorySorter.plan(inventory, SortOrder.NAME).forEach(replay::click);

        assertFalse(replay.outcomes().contains(SlotInteraction.Outcome.MERGED),
                "a click that could merge would change what the player carries");
        assertEquals(inventory.size(), replay.snapshot().size());
    }

    @Test
    void aPlanIsAPermutationOfTheStacksItStartedWith() {
        List<SlotStack> inventory = List.of(stack("Dirt", 40), stack("Dirt", 40), stack("Apple", 64), tool("Pickaxe"),
                SlotStack.EMPTY, stack("Coal", 7));

        List<SlotStack> sorted = SlotInteraction.replay(inventory, InventorySorter.plan(inventory, SortOrder.COUNT));

        assertEquals(stacksOf(inventory), stacksOf(sorted), "same stacks, new places");
        assertEquals(inventory.size(), sorted.size(), "no slot appeared or vanished");
    }

    @Test
    void everyPlanStaysPermutedAndOrderedOnRandomInventories() {
        Random random = new Random(20260929L);
        for (int round = 0; round < 200; round++) {
            List<SlotStack> inventory = randomInventory(random, 27 + random.nextInt(10));
            for (SortOrder order : SortOrder.values()) {
                List<Integer> plan = InventorySorter.plan(inventory, order);
                List<SlotStack> sorted = SlotInteraction.replay(inventory, plan);

                assertEquals(InventorySorter.desiredNames(inventory, order), namesOf(sorted),
                        "round " + round + " with " + order);
                assertEquals(stacksOf(inventory), stacksOf(sorted), "round " + round + " with " + order);
                assertTrue(plan.size() <= InventorySorter.CLICKS_PER_SWAP * inventory.size(),
                        "a plan is bounded by the slots it sorts");
            }
        }
    }

    @Test
    void sortingIsIdempotent() {
        List<SlotStack> inventory = List.of(stack("Dirt", 3), stack("Apple", 9), SlotStack.EMPTY, tool("Pickaxe"));
        List<SlotStack> sorted = SlotInteraction.replay(inventory, InventorySorter.plan(inventory, SortOrder.NAME));

        assertEquals(List.of(), InventorySorter.plan(sorted, SortOrder.NAME),
                "sorting an inventory that is already sorted does nothing");
    }

    private static List<SlotStack> randomInventory(Random random, int size) {
        List<SlotStack> slots = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            if (random.nextInt(4) == 0) {
                slots.add(SlotStack.EMPTY);
                continue;
            }
            String name = ITEMS.get(random.nextInt(ITEMS.size()));
            slots.add(random.nextInt(8) == 0 ? tool(name + " Tool") : stack(name, 1 + random.nextInt(64)));
        }
        return slots;
    }
}
