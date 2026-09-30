package wtf.woke.lite.screen;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import wtf.woke.lite.WokeLiteClient;
import wtf.woke.lite.config.ConfigManager;
import wtf.woke.lite.core.KeybindAction;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;

/**
 * The mod's config screen: the sidebar of categories on the left, the modules of
 * the selected one and their settings on the right, and a search box over both.
 *
 * <p>This class is layout and wiring only. It turns the registry into plain text
 * once per change, hands the rows to the two panes, and gives out the two ways in
 * — a keybind in Controls and {@code /wokewtf config}. Everything that draws a
 * row, decides a value or filters the list lives in the pane and row classes,
 * which is what keeps each of those small enough to read.</p>
 *
 * <p>Opened from Mod Menu it returns to the screen it came from; opened in game
 * it returns to the world. Escape closes it, clicking outside it does not — the
 * screen is modal, and a stray click in the world should not commit anything.</p>
 */
public final class WokeConfigScreen extends Screen {

    /** Title shown in the header and, indirectly, in the window list. */
    static final String TITLE_KEY = "wokewtf.lite.config.title";

    /** Shown inside the search box while it is empty. */
    static final String SEARCH_TOOLTIP_KEY = "wokewtf.lite.config.search.tooltip";

    /** Shown in the module pane when the query matches nothing. */
    static final String EMPTY_KEY = "wokewtf.lite.config.empty";

    /** Id of the mod-level settings group; deliberately not a module id. */
    private static final String GLOBAL_MODULE_ID = "global";

    /** Why that group exists, shown under its header. */
    private static final String GLOBAL_DESCRIPTION_KEY = "wokewtf.lite.config.global.description";

    /** Keybind that opens this screen, and its shipped default. */
    private static final String OPEN_KEYBIND_ID = "config.open";
    private static final String OPEN_KEYBIND_KEY = "wokewtf.lite.keybind.open_config";
    private static final String OPEN_KEYBIND_DEFAULT = "key.keyboard.right.shift";

    private static final int HEADER_HEIGHT = 30;
    private static final int SIDEBAR_WIDTH = 140;
    private static final int GAP = 6;
    private static final int MARGIN = 10;
    private static final int FIELD_HEIGHT = 16;

    private final Screen parent;
    private final ModuleRegistry registry = WokeLiteClient.registry();
    private final ConfigManager config = WokeLiteClient.config();

    private final Set<ModuleCategory> collapsed = EnumSet.allOf(ModuleCategory.class);

    private String query = "";
    private ModuleCategory selected = ModuleCategory.HUD;
    private SearchFieldWidget search;
    private CategoryListWidget categories;
    private ModuleListWidget modules;

    /** Opens the screen over the world, so closing it returns to the game. */
    public WokeConfigScreen() {
        this(null);
    }

    /** @param parent the screen to return to, or {@code null} for the game */
    public WokeConfigScreen(Screen parent) {
        super(Text.translatable(TITLE_KEY));
        this.parent = parent;
        collapsed.remove(selected);
    }

    /** Opens the screen from anywhere that has a client. */
    public static void open(Screen parent) {
        MinecraftClient.getInstance().setScreen(new WokeConfigScreen(parent));
    }

    /** @return the keybind that opens this screen, for the module registry */
    public static KeybindAction keybind() {
        return new KeybindAction(OPEN_KEYBIND_ID, OPEN_KEYBIND_KEY, KeybindAction.DEFAULT_CATEGORY_KEY,
                OPEN_KEYBIND_DEFAULT, () -> open(MinecraftClient.getInstance().currentScreen));
    }

    @Override
    protected void init() {
        int paneTop = HEADER_HEIGHT;
        int paneHeight = height - MARGIN - paneTop;
        int paneX = MARGIN + SIDEBAR_WIDTH + GAP;
        int paneWidth = width - paneX - MARGIN;

        categories = new CategoryListWidget(MARGIN, paneTop, SIDEBAR_WIDTH, paneHeight, this::select);
        modules = new ModuleListWidget(paneX, paneTop, paneWidth, paneHeight, registry, this::refresh);
        search = new SearchFieldWidget(textRenderer, paneX, MARGIN, paneWidth, FIELD_HEIGHT,
                Text.translatable("wokewtf.lite.config.search"), this::onQueryChanged);
        // After every field exists: setting the text may notify the listener.
        search.setText(query);
        categories.setSelected(selected);

        addDrawableChild(search);
        addDrawableChild(categories);
        addDrawableChild(modules);
        refresh();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ConfigTheme.fill(context, 0, 0, width, height, ConfigTheme.BACKDROP);
        ConfigTheme.fill(context, 0, 0, width, HEADER_HEIGHT - 4, ConfigTheme.PANEL);
        ConfigTheme.fill(context, 0, HEADER_HEIGHT - 4, width, 1, ConfigTheme.BORDER);
        context.drawTextWithShadow(textRenderer, title, MARGIN, 10, ConfigTheme.TEXT);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // An open value list claims Escape first: it is the innermost thing open.
        if (input.isEscape() && modules.closeOverlay()) {
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private void onQueryChanged(String text) {
        query = text;
        refresh();
    }

    private void select(ModuleCategory category) {
        if (category == selected && !collapsed.contains(category)) {
            collapsed.add(category);
        } else {
            selected = category;
            collapsed.remove(category);
        }
        categories.setSelected(selected);
        refresh();
    }

    private void refresh() {
        List<ConfigRow> rows = ConfigRows.build(snapshot(), query, collapsed);
        List<ConfigRow.CategoryRow> headers = new ArrayList<>();
        List<ConfigRow> contents = new ArrayList<>();
        for (ConfigRow row : rows) {
            if (row instanceof ConfigRow.CategoryRow header) {
                headers.add(header);
            } else {
                contents.add(row);
            }
        }
        categories.setRows(headers);
        modules.setRows(contents);
    }

    /** Turns what is registered into the text the screen shows. */
    private List<ConfigRows.ModuleText> snapshot() {
        List<ConfigRows.ModuleText> model = new ArrayList<>();
        for (QoLModule module : registry.all()) {
            model.add(new ConfigRows.ModuleText(module.id(), module.category(), text(module.translationKey()),
                    text(module.descriptionKey()), module.isEnabled(), settingsOf(module.settings())));
        }
        // The mod's own settings ride in as one more group, so the sidebar and the
        // pane need no special case for them.
        model.add(new ConfigRows.ModuleText(GLOBAL_MODULE_ID, ModuleCategory.GLOBAL,
                text(ModuleCategory.GLOBAL.translationKey()), text(GLOBAL_DESCRIPTION_KEY), true,
                settingsOf(config.globalSettings())));
        return model;
    }

    private static List<ConfigRows.SettingText> settingsOf(List<Setting<?>> settings) {
        List<ConfigRows.SettingText> texts = new ArrayList<>(settings.size());
        for (Setting<?> setting : settings) {
            texts.add(new ConfigRows.SettingText(setting, text(setting.translationKey()),
                    setting.descriptionKey().map(WokeConfigScreen::text).orElse("")));
        }
        return texts;
    }

    private static String text(String key) {
        return Text.translatable(key).getString();
    }
}
