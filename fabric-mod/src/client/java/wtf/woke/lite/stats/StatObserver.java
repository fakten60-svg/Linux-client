package wtf.woke.lite.stats;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Watches the running client and feeds the counter.
 *
 * <p>Ticks and positions are read straight off the player. Blocks are harder:
 * the client's block-break hook is not reachable from a mod without patching
 * the class, and this mod does not add mixins for features that can be observed
 * instead. So a block is counted when the block the player was <em>actively
 * breaking</em> turns into air — the position is remembered only while the
 * break is in progress and dropped the moment the player stops, so letting go
 * halfway through counts nothing.</p>
 *
 * <p>The consequence is worth stating plainly: this counts what <em>this
 * client</em> saw itself break. A block destroyed by someone else, or by an
 * explosion while the player happened to be aiming at it, is not counted, and a
 * player who breaks a block in the same tick somebody else does may count one
 * that is not theirs. It is a local tally, not a leaderboard, which is the only
 * thing it was ever meant to be.</p>
 */
public final class StatObserver {

    private BlockPos target;

    /**
     * Feeds one client tick into the counter.
     *
     * @param client  the running client
     * @param counter the counter to add to
     */
    public void observe(MinecraftClient client, StatCounter counter) {
        if (client.player == null || client.world == null) {
            target = null;
            return;
        }
        counter.addMovement(client.player.getX(), client.player.getZ());

        ClientPlayerInteractionManager interaction = client.interactionManager;
        if (interaction == null) {
            target = null;
            return;
        }
        if (interaction.isBreakingBlock() && client.crosshairTarget instanceof BlockHitResult hit) {
            target = hit.getBlockPos();
        }
        if (target == null) {
            return;
        }
        if (client.world.getBlockState(target).isAir()) {
            counter.addBlockMined();
            target = null;
        } else if (!interaction.isBreakingBlock()) {
            // The break was abandoned; stop watching a block nobody is mining.
            target = null;
        }
    }

    /** Forgets the block being watched, e.g. on a world change. */
    public void reset() {
        target = null;
    }

    /** @return whether a block break is currently being watched */
    public boolean isWatching() {
        return target != null;
    }
}
