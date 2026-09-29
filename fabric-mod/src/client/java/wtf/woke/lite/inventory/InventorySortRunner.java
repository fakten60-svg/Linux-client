package wtf.woke.lite.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import wtf.woke.lite.WokeLite;

/**
 * Replays a sort plan through the vanilla interaction manager.
 *
 * <p>It reads the player's own inventory out of {@code playerScreenHandler} —
 * the handler the server also has for that player — and clicks nothing but the
 * inventory and hotbar slots of it, with the same {@code PICKUP} action a mouse
 * click produces. The server validates every click, so this is the interaction
 * path the game already sanctions rather than a shortcut around it.</p>
 *
 * <p>A plan is replayed one click per tick and abandoned as soon as the
 * situation changes under it (a screen opens, the world goes away, the player
 * vanishes), so a half-applied plan can never race a player who is busy with
 * the same inventory.</p>
 */
public final class InventorySortRunner {

    /** What one tick of replaying did. */
    public enum Step {

        /** No plan is running. */
        IDLE,

        /** A click was issued and the plan is not done. */
        CLICKED,

        /** The plan was replayed to the end. */
        FINISHED,

        /** The plan was dropped because the situation changed. */
        ABORTED
    }

    /**
     * Clicks issued per tick. One at a time keeps the client and the server in
     * step without depending on how many clicks a tick tolerates, and a full
     * inventory is still done in a couple of seconds.
     */
    private static final int CLICKS_PER_TICK = 1;

    private List<Integer> slotIds = List.of();
    private List<Integer> schedule = List.of();
    private int nextClick;

    /** @return {@code true} while a plan is being replayed */
    public boolean isRunning() {
        return !schedule.isEmpty();
    }

    /** @return how many stacks the running plan rearranges */
    public int pendingStacks() {
        return schedule.size() / InventorySorter.CLICKS_PER_SWAP;
    }

    /**
     * Reads the inventory and plans a sort over it.
     *
     * @param order         which order to sort into
     * @param includeHotbar whether the hotbar is part of the sorted range
     * @return why a sort did or did not start; nothing runs unless it is
     *         {@link SortResult#STARTED}
     */
    public SortResult start(SortOrder order, boolean includeHotbar) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) {
            return SortResult.NOT_READY;
        }
        List<Integer> ids = sortedSlotIds(includeHotbar);
        List<Integer> plan = InventorySorter.plan(contentsOf(client.player.playerScreenHandler, ids), order);
        if (plan.isEmpty()) {
            return SortResult.ALREADY_SORTED;
        }
        slotIds = ids;
        schedule = plan;
        nextClick = 0;
        return SortResult.STARTED;
    }

    /**
     * Issues the next click of the running plan.
     *
     * @return what this tick did
     */
    public Step step() {
        if (!isRunning()) {
            return Step.IDLE;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerInteractionManager interaction = client.interactionManager;
        if (client.player == null || client.currentScreen != null || interaction == null
                || !client.isIntegratedServerRunning()) {
            cancel();
            return Step.ABORTED;
        }
        int syncId = client.player.playerScreenHandler.syncId;
        for (int click = 0; click < CLICKS_PER_TICK && nextClick < schedule.size(); click++) {
            interaction.clickSlot(syncId, slotIds.get(schedule.get(nextClick)), 0, SlotActionType.PICKUP,
                    client.player);
            nextClick++;
        }
        if (nextClick >= schedule.size()) {
            int moved = pendingStacks();
            cancel();
            WokeLite.LOGGER.info("Inventory sort finished: {} stacks reordered", moved);
            return Step.FINISHED;
        }
        return Step.CLICKED;
    }

    /** Drops the plan without touching the inventory again. */
    public void cancel() {
        nextClick = 0;
        schedule = List.of();
        slotIds = List.of();
    }

    /** @return the inventory's slot ids, in the order they should end up in */
    private static List<Integer> sortedSlotIds(boolean includeHotbar) {
        List<Integer> ids = new ArrayList<>(PlayerScreenHandler.INVENTORY_END - PlayerScreenHandler.INVENTORY_START
                + PlayerScreenHandler.HOTBAR_END - PlayerScreenHandler.HOTBAR_START);
        for (int slotId = PlayerScreenHandler.INVENTORY_START; slotId < PlayerScreenHandler.INVENTORY_END; slotId++) {
            ids.add(slotId);
        }
        if (includeHotbar) {
            for (int slotId = PlayerScreenHandler.HOTBAR_START; slotId < PlayerScreenHandler.HOTBAR_END; slotId++) {
                ids.add(slotId);
            }
        }
        return ids;
    }

    private static List<SlotStack> contentsOf(PlayerScreenHandler handler, List<Integer> slotIds) {
        List<SlotStack> contents = new ArrayList<>(slotIds.size());
        for (int slotId : slotIds) {
            contents.add(of(handler.getSlot(slotId).getStack()));
        }
        return contents;
    }

    private static SlotStack of(ItemStack stack) {
        return stack.isEmpty() ? SlotStack.EMPTY
                : new SlotStack(stack.getName().getString(), stack.getCount(), stack.getMaxCount());
    }
}
