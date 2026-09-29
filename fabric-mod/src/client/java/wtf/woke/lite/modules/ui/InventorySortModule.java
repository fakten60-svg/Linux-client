package wtf.woke.lite.modules.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.Availability;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.inventory.InventorySortRunner;
import wtf.woke.lite.inventory.SortOrder;
import wtf.woke.lite.inventory.SortResult;

/**
 * Tidies up the player's own inventory by reordering whole stacks.
 *
 * <p>Restricted to a world this client owns. The module is only
 * {@link #availability() available} while the integrated server is running,
 * which is the case in singleplayer and for the host of a LAN world, and a
 * running sort is abandoned the moment that stops being true. On a server
 * somebody else runs, this module never becomes active, so the mod never
 * rearranges an inventory the player does not own.</p>
 *
 * <p>Where it is allowed it still uses nothing but the vanilla interaction path:
 * the plan is replayed by {@link InventorySortRunner} as ordinary {@code PICKUP}
 * clicks the server validates one by one. That plan never pairs two slots
 * holding the same stackable item, so no click can turn into a merge, and the
 * result is a permutation of the stacks that were already there — never a change
 * to what or how much the player carries.</p>
 */
public final class InventorySortModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "ui.inventory_sort";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.ui.inventory_sort";

    /** Reason key while the world is not one this client owns. */
    public static final String REASON_NOT_OWN_WORLD = "wokewtf.lite.reason.own_world_only";

    private final Setting<SortOrder> order;
    private final Setting<Boolean> includeHotbar;
    private final InventorySortRunner runner = new InventorySortRunner();

    public InventorySortModule() {
        this.order = Settings.choice("order", SortOrder.NAME)
                .describedBy(settingKey("order"), settingKey("order") + ".tooltip");
        this.includeHotbar = Settings.bool("includeHotbar", false)
                .describedBy(settingKey("includeHotbar"), settingKey("includeHotbar") + ".tooltip");
    }

    @Override
    public String id() {
        return MODULE_ID;
    }

    @Override
    public String translationKey() {
        return TRANSLATION_KEY;
    }

    @Override
    public ModuleCategory category() {
        return ModuleCategory.INTERFACE;
    }

    @Override
    public void onRegister(ModuleRegistry registry) {
        addSetting(order);
        addSetting(includeHotbar);
    }

    /** Only a world this client owns may have its inventory rearranged. */
    @Override
    public Availability availability() {
        return MinecraftClient.getInstance().isIntegratedServerRunning()
                ? Availability.available()
                : Availability.unavailable(REASON_NOT_OWN_WORLD);
    }

    /** @return how many stacks the running plan rearranges, zero when idle */
    public int pendingStacks() {
        return runner.pendingStacks();
    }

    /**
     * Plans a sort of the inventory as it is now and starts replaying it.
     *
     * @return why the sort did or did not start
     */
    public SortResult startSort() {
        if (!isEnabled()) {
            return SortResult.DISABLED;
        }
        if (!availability().isAvailable()) {
            return SortResult.UNAVAILABLE;
        }
        if (runner.isRunning()) {
            return SortResult.BUSY;
        }
        SortResult result = runner.start(order.get(), includeHotbar.get());
        if (result == SortResult.STARTED) {
            WokeLite.LOGGER.info("Inventory sort started: {} stacks (order {}, hotbar {})",
                    runner.pendingStacks(), order.get(), includeHotbar.get());
        }
        return result;
    }

    @Override
    public void onClientTick() {
        if (!runner.isRunning()) {
            return;
        }
        // Read the size before stepping: the runner clears the plan to finish.
        int planned = runner.pendingStacks();
        switch (runner.step()) {
            case FINISHED -> report(Text.translatable(TRANSLATION_KEY + ".done", planned));
            case ABORTED -> report(Text.translatable(TRANSLATION_KEY + ".interrupted"));
            case IDLE, CLICKED -> { }
        }
    }

    /** A world going away takes the rest of the plan with it. */
    @Override
    public void onLeaveWorld() {
        runner.cancel();
    }

    /** Switching the module off abandons a running plan. */
    @Override
    public void onDisable() {
        runner.cancel();
    }

    private static void report(Text message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
    }

    private static String settingKey(String settingId) {
        return TRANSLATION_KEY + ".setting." + settingId;
    }
}
