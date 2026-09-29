package wtf.woke.lite.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KeybindRegistryTest {

    private static final class Fatal extends VirtualMachineError {
        private static final long serialVersionUID = 1L;

        Fatal(String message) {
            super(message);
        }
    }

    @Test
    void registrationKeepsOrderAndRejectsDuplicates() {
        KeybindRegistry registry = new KeybindRegistry();
        registry.register(new KeybindAction("b", "label.b", null, "key.keyboard.b", () -> { }));
        registry.register(new KeybindAction("a", "label.a", null, "key.keyboard.a", () -> { }));

        assertEquals(List.of("b", "a"), registry.all().stream().map(KeybindAction::id).toList());
        assertEquals(2, registry.size());
        assertTrue(registry.isRegistered("a"));
        assertEquals("key.keyboard.a", registry.defaultKey("a"));
        assertEquals(KeybindAction.UNBOUND, registry.defaultKey("missing"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new KeybindAction("a", "dup", null, null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new KeybindAction("  ", null, null, null, null)));
    }

    @Test
    void theRecordNormalisesMissingMetadata() {
        KeybindAction action = new KeybindAction("screenshot", null, null, null, null);

        assertEquals("wokewtf.lite.keybind.screenshot", action.translationKey());
        assertEquals(KeybindAction.DEFAULT_CATEGORY_KEY, action.categoryKey());
        assertEquals(KeybindAction.UNBOUND, action.defaultKey());
        action.run();
    }

    @Test
    void triggeringRunsTheHandlerOnceAndReportsUnknownIds() {
        KeybindRegistry registry = new KeybindRegistry();
        AtomicInteger runs = new AtomicInteger();
        registry.register(new KeybindAction("macro", "label", null, "key.keyboard.m", runs::incrementAndGet));

        assertFalse(registry.trigger("missing"), "an unregistered id is not a trigger");
        assertTrue(registry.trigger("macro"));
        assertEquals(1, runs.get());
        assertTrue(registry.byId("macro").isPresent());
        assertTrue(registry.byId("missing").isEmpty());
    }

    @Test
    void aThrowingHandlerIsLoggedNotPropagated() {
        KeybindRegistry registry = new KeybindRegistry();
        registry.register(new KeybindAction("bad", "label", null, null, () -> {
            throw new IllegalStateException("planned handler failure");
        }));

        assertTrue(registry.trigger("bad"), "the id existed, so the press counted");
        assertTrue(registry.isRegistered("bad"), "a broken shortcut does not unregister itself");
    }

    @Test
    void jvmLevelFailuresAreNotSwallowed() {
        KeybindRegistry registry = new KeybindRegistry();
        registry.register(new KeybindAction("fatal", "label", null, null, () -> {
            throw new Fatal("planned");
        }));

        assertThrows(Fatal.class, () -> registry.trigger("fatal"));
    }
}
