package wtf.woke.lite;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import wtf.woke.lite.screen.WokeConfigScreen;

/**
 * The optional Mod Menu hook: it is what puts a "Configure" button on this mod's
 * entry in the mod list.
 *
 * <p>Soft, in both directions. Mod Menu is only <em>suggested</em> in the
 * metadata and only on the compile classpath, so nothing here is loaded when Mod
 * Menu is absent — Fabric only instantiates the {@code modmenu} entrypoint when
 * Mod Menu itself asks for it — and the mod starts exactly as before without it.
 * There is no hard dependency to fail on.</p>
 *
 * <p>The factory returns a fresh screen per call rather than a shared one: the
 * screen reads the live registry when it opens, and opening it twice must not
 * reuse a screen that has already been closed.</p>
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WokeConfigScreen::new;
    }
}
