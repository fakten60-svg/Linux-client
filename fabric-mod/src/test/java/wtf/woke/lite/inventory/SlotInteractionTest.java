package wtf.woke.lite.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SlotInteractionTest {

    private static SlotStack stack(String name, int count) {
        return new SlotStack(name, count, 64);
    }

    @Test
    void aClickFollowsTheFourVanillaRules() {
        SlotInteraction interaction = new SlotInteraction(List.of(stack("Stone", 5), SlotStack.EMPTY));

        assertEquals(SlotInteraction.Outcome.NOTHING, interaction.click(1), "empty slot, empty cursor");
        assertEquals(SlotInteraction.Outcome.PICKED_UP, interaction.click(0), "the cursor takes the stack");
        assertEquals(SlotStack.EMPTY, interaction.snapshot().get(0));
        assertEquals(stack("Stone", 5), interaction.cursor());
        assertEquals(SlotInteraction.Outcome.PUT_DOWN, interaction.click(1), "the cursor fills an empty slot");
        assertEquals(stack("Stone", 5), interaction.snapshot().get(1));
        assertEquals(SlotStack.EMPTY, interaction.cursor());
        assertEquals(3, interaction.outcomes().size());
    }

    @Test
    void differentItemsTradePlacesWithTheCursor() {
        SlotInteraction interaction = new SlotInteraction(List.of(stack("Stone", 5), stack("Apple", 3)));

        interaction.click(0);
        assertEquals(SlotInteraction.Outcome.SWAPPED, interaction.click(1));
        assertEquals(SlotStack.EMPTY, interaction.snapshot().get(0));
        assertEquals(stack("Stone", 5), interaction.snapshot().get(1));
        assertEquals(stack("Apple", 3), interaction.cursor(), "the displaced stack rides the cursor");

        assertEquals(SlotInteraction.Outcome.PUT_DOWN, interaction.click(0));
        assertEquals(List.of(stack("Apple", 3), stack("Stone", 5)), interaction.snapshot(),
                "three clicks trade the two stacks");
        assertEquals(SlotStack.EMPTY, interaction.cursor());
    }

    @Test
    void sameItemsCombineOnlyAsFarAsTheyFit() {
        SlotInteraction interaction = new SlotInteraction(List.of(stack("Stone", 40), stack("Stone", 40)));

        interaction.click(1);
        assertEquals(SlotInteraction.Outcome.MERGED, interaction.click(0));
        assertEquals(stack("Stone", 64), interaction.snapshot().get(0));
        assertEquals(stack("Stone", 16), interaction.cursor(), "the remainder stays on the cursor");

        interaction.click(1);
        assertEquals(stack("Stone", 16), interaction.snapshot().get(1));
    }

    @Test
    void combiningIntoAFullStackChangesNothing() {
        SlotInteraction interaction = new SlotInteraction(List.of(stack("Stone", 5), stack("Stone", 64)));

        interaction.click(0);
        assertEquals(SlotInteraction.Outcome.NOTHING, interaction.click(1));
        assertEquals(stack("Stone", 64), interaction.snapshot().get(1));
        assertEquals(stack("Stone", 5), interaction.cursor());
    }

    @Test
    void unstackableItemsAreNeverCombinedEvenWhenTheyShareAName() {
        SlotStack pickaxe = new SlotStack("Pickaxe", 1, 1);
        SlotInteraction interaction = new SlotInteraction(List.of(pickaxe, pickaxe));

        interaction.click(0);
        assertEquals(SlotInteraction.Outcome.SWAPPED, interaction.click(1));
        assertEquals(SlotStack.EMPTY, interaction.snapshot().get(0), "nothing was combined");
        assertEquals(pickaxe, interaction.cursor());

        interaction.click(0);
        assertEquals(List.of(pickaxe, pickaxe), interaction.snapshot());
    }

    @Test
    void replayingAClickSequenceHandsBackTheStartingInventory() {
        List<SlotStack> initial = List.of(stack("Stone", 5), stack("Apple", 3), SlotStack.EMPTY);

        assertEquals(List.of(stack("Apple", 3), stack("Stone", 5), SlotStack.EMPTY),
                SlotInteraction.replay(initial, List.of(0, 1, 0)));
        assertEquals(initial, SlotInteraction.replay(initial, List.of(0, 0)),
                "picking a stack up and putting it straight back changes nothing");
    }

    @Test
    void clickingOutsideTheInventoryIsRefused() {
        SlotInteraction interaction = new SlotInteraction(List.of(stack("Stone", 5)));

        assertThrows(IndexOutOfBoundsException.class, () -> interaction.click(1));
        assertThrows(IndexOutOfBoundsException.class, () -> interaction.click(-1));
        assertEquals(1, interaction.size());
    }
}
