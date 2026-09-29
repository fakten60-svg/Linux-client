package wtf.woke.lite.modules.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.KeybindAction;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.reconnect.ReconnectStateMachine;

/**
 * Comes back to the server you were on after the connection dropped.
 *
 * <p>Off by default, and loud when it runs: the countdown is printed in chat
 * line by line, and a keybind cancels it. Nothing connects without the player
 * having seen a countdown first, which is what "no silent reconnect" means
 * here.</p>
 *
 * <p>It also cannot get anyone past a ban. The reconnect goes through the
 * ordinary vanilla connect flow with the player's own session, from the same
 * address, so a server that refused them refuses them again; and an attempt is
 * made once and never retried, because a retry loop is exactly what turning a
 * ban into a connectivity problem would look like. See
 * {@link ReconnectStateMachine} for how the single attempt is enforced.</p>
 *
 * <p>Only multiplayer is served: in singleplayer the world lives in this
 * process and there is nothing to reconnect to. The sequence itself is
 * Minecraft-free and unit-tested; this class only drives it and talks to the
 * chat hud.</p>
 */
public final class AutoReconnectModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "util.auto_reconnect";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.util.auto_reconnect";

    /** Keybind that stops a running countdown. */
    public static final String CANCEL_KEYBIND_ID = "util.auto_reconnect.cancel";

    /** Bounds and default of the countdown, in seconds. */
    public static final int MIN_SECONDS = 3;
    public static final int MAX_SECONDS = 60;
    public static final int DEFAULT_SECONDS = 10;

    private final Setting<Integer> countdownSeconds;
    private final ReconnectStateMachine machine = new ReconnectStateMachine();

    private ServerInfo lastServer;
    private int announcedSecond = -1;

    public AutoReconnectModule() {
        this.countdownSeconds = Settings.integer("countdownSeconds", DEFAULT_SECONDS, MIN_SECONDS, MAX_SECONDS)
                .describedBy(key("countdownSeconds"), key("countdownSeconds") + ".tooltip");
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
        addSetting(countdownSeconds);
        registry.keybinds().register(new KeybindAction(CANCEL_KEYBIND_ID, TRANSLATION_KEY + ".keybind.cancel",
                null, KeybindAction.UNBOUND, this::cancelFromKeybind));
    }

    /** Remembers the server to come back to while the connection is still live. */
    public void onJoined(MinecraftClient client) {
        lastServer = client.getCurrentServerEntry();
        machine.markConnected();
    }

    /**
     * Arms the countdown if this disconnect is one this module should answer.
     *
     * @param client the client, whose current screen is not the disconnect
     *               screen yet — it is only read when the countdown fires
     */
    public void onDisconnected(MinecraftClient client) {
        if (!isEnabled() || client.isIntegratedServerRunning()) {
            machine.reset();
            return;
        }
        if (lastServer == null || lastServer.address == null || lastServer.address.isBlank()) {
            return;
        }
        if (machine.start(countdownSeconds.get() * 20, ReconnectStateMachine.DEFAULT_ATTEMPT_TIMEOUT_TICKS)) {
            announcedSecond = -1;
            report(Text.translatable(TRANSLATION_KEY + ".lost", lastServer.name, countdownSeconds.get()));
            WokeLite.LOGGER.info("Connection to {} lost; reconnecting in {}s (bind {} to cancel)",
                    lastServer.address, countdownSeconds.get(), CANCEL_KEYBIND_ID);
        }
    }

    @Override
    public void onClientTick() {
        if (!machine.isCounting() && !machine.isConnecting()) {
            return;
        }
        switch (machine.tick()) {
            case CONNECT -> connect(MinecraftClient.getInstance());
            case COUNTING -> announce();
            case TIMED_OUT -> report(Text.translatable(TRANSLATION_KEY + ".timeout"));
            case WAITING -> { }
        }
    }

    /** Switching the module off abandons a countdown that is still running. */
    @Override
    public void onDisable() {
        machine.reset();
    }

    @Override
    public void onEnable() {
        machine.reset();
    }

    /** @return the state machine, so its state can be reported and tested */
    public ReconnectStateMachine machine() {
        return machine;
    }

    private void cancelFromKeybind() {
        if (isEnabled()) {
            cancel();
        }
    }

    /** Stops a running countdown, saying so when there was one. */
    public void cancel() {
        if (machine.cancel()) {
            report(Text.translatable(TRANSLATION_KEY + ".cancelled"));
        }
    }

    private void announce() {
        int seconds = machine.remainingSeconds();
        if (seconds == announcedSecond) {
            return;
        }
        announcedSecond = seconds;
        report(Text.translatable(TRANSLATION_KEY + ".countdown", seconds));
    }

    private void connect(MinecraftClient client) {
        report(Text.translatable(TRANSLATION_KEY + ".connecting", lastServer.name));
        Screen parent = client.currentScreen == null ? new TitleScreen() : client.currentScreen;
        try {
            ConnectScreen.connect(parent, client, ServerAddress.parse(lastServer.address), lastServer, false, null);
            WokeLite.LOGGER.info("Reconnect attempt for {} started", lastServer.address);
        } catch (Exception failure) {
            // A malformed address is the one thing here that is not the game's
            // to validate; give up loudly rather than half-connecting.
            machine.cancel();
            report(Text.translatable(TRANSLATION_KEY + ".failed", lastServer.address));
            WokeLite.LOGGER.warn("Reconnect to {} could not be started", lastServer.address, failure);
        }
    }

    private static void report(Text message) {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
    }

    private static String key(String settingId) {
        return TRANSLATION_KEY + ".setting." + settingId;
    }
}
