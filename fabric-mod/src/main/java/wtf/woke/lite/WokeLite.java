package wtf.woke.lite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod-wide constants shared by both source sets.
 *
 * <p>This class deliberately carries no Minecraft imports so that anything
 * depending on it stays loadable outside a running game, which keeps the
 * framework layer unit-testable.</p>
 */
public final class WokeLite {

    /** Mod id. Must stay in sync with {@code id} in {@code fabric.mod.json}. */
    public static final String MOD_ID = "wokewtf-lite";

    /** Human-readable mod name used in logs and the config screen title. */
    public static final String MOD_NAME = "woke.wtf Lite";

    /**
     * Shared logger. Minecraft 1.21.11 bundles {@code org.slf4j:slf4j-api},
     * so no logging dependency is shipped with the mod.
     */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private WokeLite() {
        throw new AssertionError("No instances of " + WokeLite.class.getName());
    }
}
