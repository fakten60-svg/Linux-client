package wtf.woke.lite.modules.ui;

import java.time.LocalTime;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import wtf.woke.lite.chat.ChatHistory;
import wtf.woke.lite.chat.ChatTimestampStyle;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;

/**
 * Local chat display: timestamps on each line and a longer scrollback.
 *
 * <p>Client-side only. It changes nothing about what is sent, received or
 * stored on the server; the timestamp is added to the copy the client is about
 * to show, and the history limit only decides how much of the client's own
 * scrollback is kept in memory.</p>
 *
 * <p>The formatting rules live in {@code wtf.woke.lite.chat} (no game needed)
 * and the injection points in
 * {@code wtf.woke.lite.mixin.client.ChatHudMixin}.</p>
 */
public final class ChatModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "ui.chat";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.ui.chat";

    /** Timestamp colour a fresh install starts with. */
    public static final Formatting DEFAULT_TIMESTAMP_COLOR = Formatting.DARK_GRAY;

    private final Setting<Boolean> timestamps;
    private final Setting<ChatTimestampStyle> timestampStyle;
    private final Setting<Integer> historyLength;

    public ChatModule() {
        this.timestamps = Settings.bool("timestamps", true)
                .describedBy(key("timestamps"), key("timestamps") + ".tooltip");
        this.timestampStyle = Settings.choice("timestampStyle", ChatTimestampStyle.HOUR_MINUTE)
                .describedBy(key("timestampStyle"), null);
        this.historyLength = Settings
                .integer("historyLength", ChatHistory.DEFAULT_LENGTH, ChatHistory.MIN_LENGTH, ChatHistory.MAX_LENGTH)
                .describedBy(key("historyLength"), key("historyLength") + ".tooltip");
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
        addSetting(timestamps);
        addSetting(timestampStyle);
        addSetting(historyLength);
    }

    /**
     * @param message the line about to enter the chat hud
     * @return the same line prefixed with the current time, or {@code message}
     *         when timestamps are off
     */
    public Text timestamped(Text message) {
        if (!timestamps.get()) {
            return message;
        }
        MutableText prefix = Text.literal(timestampStyle.get().prefix(LocalTime.now()))
                .formatted(DEFAULT_TIMESTAMP_COLOR);
        return prefix.append(message);
    }

    /** @return how many chat lines to keep, never fewer than vanilla's own cap */
    public int effectiveHistoryLength() {
        return ChatHistory.limitFor(historyLength.get());
    }

    private static String key(String settingId) {
        return TRANSLATION_KEY + ".setting." + settingId;
    }
}
