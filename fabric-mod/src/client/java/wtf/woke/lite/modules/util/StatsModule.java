package wtf.woke.lite.modules.util;

import net.minecraft.client.MinecraftClient;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.stats.StatCounter;
import wtf.woke.lite.stats.StatObserver;
import wtf.woke.lite.stats.StatSnapshot;

/**
 * Counts what this client has done on its own.
 *
 * <p>Blocks mined, distance walked and playtime, kept in the config file and
 * nowhere else. Nothing is sent to a server, nothing is uploaded and there is
 * no ranking: the numbers exist for the player who produced them and for nobody
 * else. The counting itself is Minecraft-free and unit-tested in
 * {@link StatCounter}; this class only owns the settings and the flush.</p>
 *
 * <p>The counters are written back to the settings on a slow interval rather
 * than every tick. The config autosaves a few seconds after a change, so a
 * per-tick write would mean a file write every few seconds for the whole
 * session; the interval keeps that to a trickle while still bounding what a
 * crash can lose. Leaving the world flushes as well, so the number is current
 * the moment a session ends.</p>
 */
public final class StatsModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "util.stats";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.util.stats";

    /** How often the counters are written back to the settings, in ticks. */
    public static final int FLUSH_INTERVAL_TICKS = 20 * 30;

    private final Setting<Integer> blocksMined;
    private final Setting<Double> distanceWalked;
    private final Setting<Integer> playTimeTicks;

    private final StatCounter counter = new StatCounter();
    private final StatObserver observer = new StatObserver();
    private int ticksSinceFlush;

    public StatsModule() {
        this.blocksMined = Settings.integer("blocksMined", 0, 0, Integer.MAX_VALUE)
                .describedBy(key("blocksMined"), null);
        this.distanceWalked = Settings.decimal("distanceWalked", 0.0, 0.0, Double.MAX_VALUE)
                .describedBy(key("distanceWalked"), null);
        this.playTimeTicks = Settings.integer("playTimeTicks", 0, 0, Integer.MAX_VALUE)
                .describedBy(key("playTimeTicks"), null);
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
        return ModuleCategory.CONVENIENCE;
    }

    @Override
    public void onRegister(ModuleRegistry registry) {
        addSetting(blocksMined);
        addSetting(distanceWalked);
        addSetting(playTimeTicks);
    }

    /** Picks the stored counters back up. */
    @Override
    public void onEnable() {
        counter.restore(stored());
        observer.reset();
        ticksSinceFlush = 0;
        WokeLite.LOGGER.info("Local stats resumed: {} blocks, {} blocks walked, {}s played",
                snapshot().blocksMined(), Math.round(snapshot().distanceWalked()), snapshot().playTimeSeconds());
    }

    @Override
    public void onDisable() {
        flush();
        observer.reset();
    }

    /** A world change puts the player somewhere unrelated: start measuring again. */
    @Override
    public void onJoinWorld() {
        counter.forgetPosition();
        observer.reset();
    }

    /** The counters are flushed while the config can still be written. */
    @Override
    public void onLeaveWorld() {
        flush();
        counter.forgetPosition();
        observer.reset();
    }

    @Override
    public void onClientTick() {
        counter.addTick();
        observer.observe(MinecraftClient.getInstance(), counter);
        if (++ticksSinceFlush >= FLUSH_INTERVAL_TICKS) {
            ticksSinceFlush = 0;
            flush();
        }
    }

    /** @return the counters as they stand right now */
    public StatSnapshot snapshot() {
        return counter.snapshot();
    }

    /** Writes the counters back into the settings they are stored in. */
    public void flush() {
        StatSnapshot snapshot = counter.snapshot();
        blocksMined.set(clamp(snapshot.blocksMined()));
        distanceWalked.set(snapshot.distanceWalked());
        playTimeTicks.set(clamp(snapshot.playTimeTicks()));
    }

    /** Throws the counters away and stores the result. */
    public void resetCounters() {
        counter.reset();
        observer.reset();
        flush();
    }

    private StatSnapshot stored() {
        return new StatSnapshot(blocksMined.get(), distanceWalked.get(), playTimeTicks.get());
    }

    private static int clamp(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static String key(String settingId) {
        return TRANSLATION_KEY + ".setting." + settingId;
    }
}
