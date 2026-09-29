package wtf.woke.lite.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import wtf.woke.lite.WokeLite;

/**
 * Holds the keybinds this mod wants, independently of Minecraft.
 *
 * <p>The client layer registers each of these with Fabric's key-binding API
 * during initialisation and calls {@link #trigger(String)} when one is pressed,
 * so binding metadata and press handling are separate concerns and both are
 * testable without a game.</p>
 */
public final class KeybindRegistry {

    private final Map<String, KeybindAction> actions = new LinkedHashMap<>();

    /**
     * @throws IllegalArgumentException if the id is blank or already registered
     */
    public void register(KeybindAction action) {
        Objects.requireNonNull(action, "action");
        if (action.id().isBlank()) {
            throw new IllegalArgumentException("keybind id must not be blank");
        }
        if (actions.containsKey(action.id())) {
            throw new IllegalArgumentException("duplicate keybind id '" + action.id() + "'");
        }
        actions.put(action.id(), action);
    }

    /** @return every keybind, in registration order */
    public List<KeybindAction> all() {
        return List.copyOf(actions.values());
    }

    public Optional<KeybindAction> byId(String id) {
        return Optional.ofNullable(actions.get(id));
    }

    public boolean isRegistered(String id) {
        return actions.containsKey(id);
    }

    public int size() {
        return actions.size();
    }

    /** @return the shipped default GLFW key name, or {@code "key.keyboard.unknown"} */
    public String defaultKey(String id) {
        KeybindAction action = actions.get(id);
        return action == null ? KeybindAction.UNBOUND : action.defaultKey();
    }

    /**
     * Runs the action bound to {@code id}.
     *
     * <p>A throwing handler is logged and swallowed, matching
     * {@link ModuleDispatcher#guard}: a broken shortcut must not crash the
     * input thread.</p>
     *
     * @return {@code true} when the id was registered (whether or not its
     *         handler then threw)
     */
    public boolean trigger(String id) {
        KeybindAction action = actions.get(id);
        if (action == null) {
            return false;
        }
        try {
            action.run();
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable failure) {
            WokeLite.LOGGER.error("Keybind '{}' handler threw", id, failure);
        }
        return true;
    }
}
