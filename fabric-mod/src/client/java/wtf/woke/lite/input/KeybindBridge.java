package wtf.woke.lite.input;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.KeybindAction;
import wtf.woke.lite.core.KeybindRegistry;
import wtf.woke.lite.keybind.KeybindConflicts;

/**
 * Turns the mod's own keybind declarations into real game bindings.
 *
 * <p>{@link KeybindRegistry} describes what the mod wants; this class is the
 * only place that knows how the game registers it. Every binding goes through
 * {@code KeyBindingHelper}, so all of them appear in the vanilla Controls
 * screen and are saved by the game like any other binding — nothing here needs
 * its own settings entry.</p>
 *
 * <p>Presses are routed back through {@link KeybindRegistry#trigger}, so a
 * handler that throws is logged and dropped exactly as it is on every other
 * path into a module.</p>
 *
 * <p>One thing deserves care: the game fires <em>every</em> binding that shares
 * a key, so a careless default would leave the second action looking broken with
 * nothing saying why. {@link #conflicts} reports those pairs, and the caller
 * says so out loud.</p>
 */
public final class KeybindBridge {

    /**
     * Category every binding this mod adds is filed under in Controls.
     *
     * <p>The game builds the label key from the identifier as
     * {@code key.category.<namespace>.<path>}, which is why the language entry
     * lives outside this mod's own prefix.</p>
     */
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(WokeLite.MOD_ID, "main"));

    private final KeybindRegistry keybinds;
    private final Map<String, KeyBinding> bindings = new LinkedHashMap<>();

    /** @param keybinds the registry the modules declared their bindings in */
    public KeybindBridge(KeybindRegistry keybinds) {
        this.keybinds = keybinds;
    }

    /**
     * Registers every declared binding with the game.
     *
     * <p>Called once, after the modules have declared what they want. Calling it
     * twice is harmless: a binding already installed is left alone.</p>
     *
     * @return how many bindings this call added
     */
    public int install() {
        int added = 0;
        for (KeybindAction action : keybinds.all()) {
            if (bindings.containsKey(action.id())) {
                continue;
            }
            bindings.put(action.id(), KeyBindingHelper.registerKeyBinding(create(action)));
            added++;
        }
        return added;
    }

    /** Runs the handler of every binding that was pressed since the last call. */
    public void poll() {
        for (Map.Entry<String, KeyBinding> entry : bindings.entrySet()) {
            while (entry.getValue().wasPressed()) {
                keybinds.trigger(entry.getKey());
            }
        }
    }

    /** @return how many bindings are installed */
    public int size() {
        return bindings.size();
    }

    /**
     * Finds keys this mod and something else both want.
     *
     * <p>Reads the live bound keys, not the shipped defaults, so a rebind on
     * either side is caught. The game's own bindings are labelled with their
     * translation key, which is the only stable name they have.</p>
     *
     * <p>Only pairs that involve one of this mod's bindings are returned. The
     * vanilla bindings collide with each other on purpose — the debug keys share
     * the movement keys — and reporting those would drown the pairs a player can
     * actually act on. {@link KeybindConflicts#involving} does that filtering, so
     * the definition of "ours" stays in this one place.</p>
     *
     * @return every conflicting key claimed by one of this mod's bindings and
     *         anything else registered with the game
     */
    public List<KeybindConflicts.Conflict> conflicts(MinecraftClient client) {
        Map<String, String> ownerToKey = new LinkedHashMap<>();
        for (KeybindAction action : keybinds.all()) {
            KeyBinding binding = bindings.get(action.id());
            if (binding != null) {
                ownerToKey.put(action.id(), binding.getBoundKeyTranslationKey());
            }
        }
        for (KeyBinding vanilla : client.options.allKeys) {
            if (!isOurs(vanilla)) {
                ownerToKey.putIfAbsent(vanilla.getId(), vanilla.getBoundKeyTranslationKey());
            }
        }
        return KeybindConflicts.involving(ownerToKey, Set.copyOf(bindings.keySet()));
    }

    private boolean isOurs(KeyBinding candidate) {
        for (KeyBinding binding : bindings.values()) {
            if (binding == candidate) {
                return true;
            }
        }
        return false;
    }

    private static KeyBinding create(KeybindAction action) {
        InputUtil.Key defaultKey = InputUtil.fromTranslationKey(action.defaultKey());
        return new KeyBinding(action.translationKey(), defaultKey.getCategory(), defaultKey.getCode(), CATEGORY);
    }

    /** @return the ids of the installed bindings, for logging */
    public List<String> ids() {
        return new ArrayList<>(bindings.keySet());
    }
}
