package wtf.woke.lite.modules.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.screenshot.ScreenshotWatcher;
import wtf.woke.lite.screenshot.ShareLink;

/**
 * Turns a taken screenshot into something that can be pasted right away.
 *
 * <p>Client-side and local: it puts a reference to a new screenshot on the
 * system clipboard and, optionally, says so in chat. Nothing is uploaded, no
 * server is contacted, and the screenshot file itself is only ever read by name
 * — the mod never opens, copies or transmits its contents. The reference is the
 * file path, or a {@code file://} link when share links are switched on, which
 * the game's own click-to-open message cannot give you because a click is not
 * text you can copy.</p>
 *
 * <p>Two sources report screenshots: the game's own recorder hands over the
 * exact file it wrote (see {@code ScreenshotShareMixin}), and a directory scan
 * catches anything that arrived another way. Each file is reported once, by
 * whichever source saw it first, and all the reporting happens on the client
 * tick so a file being written never gets chat or clipboard work.</p>
 */
public final class ScreenshotModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "ui.screenshot";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.ui.screenshot";

    /** How often the screenshot directory is checked for files the game missed. */
    public static final int POLL_INTERVAL_TICKS = 20;

    private final Setting<Boolean> copyPath;
    private final Setting<Boolean> notify;
    private final Setting<Boolean> shareLink;
    private final ScreenshotWatcher watcher;
    private final Path directory;

    private int ticksSincePoll;

    /**
     * @param screenshotDirectory the directory the game writes screenshots into
     */
    public ScreenshotModule(Path screenshotDirectory) {
        this.directory = Objects.requireNonNull(screenshotDirectory, "screenshotDirectory")
                .toAbsolutePath()
                .normalize();
        this.watcher = new ScreenshotWatcher(directory);
        this.copyPath = Settings.bool("copyPath", true)
                .describedBy(key("copyPath"), key("copyPath") + ".tooltip");
        this.notify = Settings.bool("notify", true).describedBy(key("notify"), null);
        this.shareLink = Settings.bool("shareLink", false)
                .describedBy(key("shareLink"), key("shareLink") + ".tooltip");
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
        addSetting(copyPath);
        addSetting(notify);
        addSetting(shareLink);
    }

    /** When the module comes on, everything already on disk has been dealt with. */
    @Override
    public void onEnable() {
        watcher.prime();
        ticksSincePoll = 0;
    }

    /**
     * Records a screenshot the game reported itself.
     *
     * <p>Called from the game's screenshot code, which writes the file on its
     * file-writing thread. This only notes the file — the report happens on the
     * client tick — so nothing here may touch the client.</p>
     *
     * @param fileName the name of the file the game just wrote
     */
    public void screenshotSaved(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        watcher.offer(directory.resolve(fileName));
    }

    @Override
    public void onClientTick() {
        // Files the game reported are handled at once; the scan is only a
        // fallback, so it keeps its slower interval.
        reportAll(watcher.takeOffered());
        if (++ticksSincePoll < POLL_INTERVAL_TICKS) {
            return;
        }
        ticksSincePoll = 0;
        reportAll(watcher.poll());
    }

    private void reportAll(List<Path> screenshots) {
        for (Path screenshot : screenshots) {
            report(screenshot);
        }
    }

    private void report(Path screenshot) {
        boolean asLink = shareLink.get();
        String reference = asLink ? ShareLink.fileUri(screenshot) : screenshot.toString();
        MinecraftClient client = MinecraftClient.getInstance();
        if (copyPath.get()) {
            client.keyboard.setClipboard(reference);
        }
        if (notify.get()) {
            client.inGameHud.getChatHud()
                    .addMessage(Text.translatable(TRANSLATION_KEY + (asLink ? ".shared" : ".saved"), reference));
        }
        WokeLite.LOGGER.info("Detected screenshot {} (clipboard {}, reported as {})", screenshot,
                copyPath.get() ? "updated" : "left alone", asLink ? "a share link" : "a path");
    }

    private static String key(String settingId) {
        return TRANSLATION_KEY + ".setting." + settingId;
    }
}
