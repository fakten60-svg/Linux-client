package wtf.woke.lite.core;

import java.util.Objects;

/**
 * A keybind this mod wants bound, plus what to run when it fires.
 *
 * <p>Deliberately free of Minecraft types: the client layer turns each of
 * these into a real {@code KeyBinding} through Fabric's key-binding API, so
 * the definitions can be registered, listed and tested without a game
 * instance.</p>
 *
 * <p>There is no category field on purpose: the game files every binding under
 * one {@code KeyBinding.Category}, which {@code KeybindBridge} owns, and a
 * per-binding category key here could not be honoured anyway — the game builds
 * its label from the category's {@code Identifier}, not from a translation key.</p>
 *
 * @param id             stable id, unique across the mod
 * @param translationKey label shown in the vanilla Controls screen
 * @param defaultKey     GLFW key name, e.g. {@code key.keyboard.g}
 * @param handler        action to run when the key is pressed
 */
public record KeybindAction(String id, String translationKey, String defaultKey, Runnable handler) {

    /** GLFW name used when a keybind ships unbound. */
    public static final String UNBOUND = "key.keyboard.unknown";

    public KeybindAction {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("keybind id must not be blank");
        }
        translationKey = translationKey == null || translationKey.isBlank()
                ? "wokewtf.lite.keybind." + id
                : translationKey;
        defaultKey = defaultKey == null || defaultKey.isBlank() ? UNBOUND : defaultKey;
        handler = handler == null ? () -> { } : handler;
    }

    /** Runs the handler. Callers that need isolation should use {@link KeybindRegistry}. */
    public void run() {
        handler.run();
    }
}
