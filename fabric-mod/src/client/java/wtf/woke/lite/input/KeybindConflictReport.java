package wtf.woke.lite.input;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.keybind.KeybindConflicts;

/**
 * Says out loud when two bindings share a key.
 *
 * <p>The game fires both and reports nothing, which leaves the second action
 * looking broken for no visible reason. One line at startup and one per world
 * join is enough to explain it without nagging.</p>
 *
 * <p>Only conflicts that involve one of this mod's own bindings reach here —
 * {@link KeybindBridge#conflicts} drops the rest — so neither the log nor the
 * chat mentions a vanilla-only pair such as the debug keys sharing the
 * movement keys.</p>
 *
 * <p>An instance rather than a static helper because the startup report has to
 * wait: during the client entrypoint the game has not built its options yet, so
 * the bindings cannot be read. Ticking once is the earliest moment the answer
 * exists, and the instance is what remembers that the startup report is done.</p>
 */
public final class KeybindConflictReport {

    /** Translation key of the single line this puts in chat. */
    public static final String MESSAGE_KEY = "wokewtf.lite.keybind.conflict";

    private boolean reportedAtStartup;

    /**
     * Reports once, on the first call.
     *
     * @param keybinds the mod's bindings
     */
    public void reportOnce(KeybindBridge keybinds) {
        if (reportedAtStartup) {
            return;
        }
        reportedAtStartup = true;
        report(keybinds);
    }

    /**
     * Reports every time it is called, for the per-join check: either side can
     * be rebound between sessions, so the answer has to be recomputed.
     *
     * @param keybinds the mod's bindings
     */
    public void report(KeybindBridge keybinds) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<KeybindConflicts.Conflict> conflicts = keybinds.conflicts(client);
        if (conflicts.isEmpty()) {
            return;
        }
        List<String> keys = conflicts.stream().map(KeybindConflicts.Conflict::key).toList();
        for (KeybindConflicts.Conflict conflict : conflicts) {
            WokeLite.LOGGER.warn("Keybind conflict: {} is shared by {}", conflict.key(),
                    String.join(" and ", conflict.owners()));
        }
        client.inGameHud.getChatHud().addMessage(
                Text.translatable(MESSAGE_KEY, conflicts.size(), String.join(", ", keys)));
    }
}
