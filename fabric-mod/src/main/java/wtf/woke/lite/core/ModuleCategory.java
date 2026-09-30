package wtf.woke.lite.core;

/**
 * Grouping used by the config screen sidebar and by the module list.
 *
 * <p>The slugs are stable identifiers: they appear in translation keys and in
 * the module ids, so renaming a constant must not change its slug without a
 * config-schema migration.</p>
 */
public enum ModuleCategory {

    /** Read-only on-screen readouts. */
    HUD("hud"),

    /** Client-side interface changes. */
    INTERFACE("ui"),

    /** Local convenience features. */
    CONVENIENCE("util"),

    /**
     * The mod's own settings rather than a module's.
     *
     * <p>No module declares this: it exists so the config screen's sidebar can
     * show the mod-level settings in the same shape as everything else, instead
     * of being a special case bolted onto the screen. It is declared last so it
     * sorts to the bottom of that sidebar.</p>
     */
    GLOBAL("global");

    private final String slug;

    ModuleCategory(String slug) {
        this.slug = slug;
    }

    /** @return the stable identifier used inside module ids */
    public String slug() {
        return slug;
    }

    /** @return the translation key for this category's display name */
    public String translationKey() {
        return "wokewtf.lite.category." + slug;
    }
}
