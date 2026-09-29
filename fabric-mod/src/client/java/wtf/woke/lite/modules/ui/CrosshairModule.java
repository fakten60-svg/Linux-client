package wtf.woke.lite.modules.ui;

import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.hud.CrosshairGeometry;
import wtf.woke.lite.hud.CrosshairStyle;

/**
 * Appearance of the crosshair drawn at the centre of the screen.
 *
 * <p>Purely cosmetic: it replaces what the client paints at the screen centre
 * and changes nothing about aiming, hitboxes, reach or what the server sees.
 * {@code HIDDEN} is the same kind of display preference as turning the HUD off
 * with F1.</p>
 *
 * <p>The module owns nothing but settings; the shape arithmetic lives in
 * {@link CrosshairGeometry} (which needs no game) and the drawing in
 * {@code wtf.woke.lite.hud.CrosshairOverlay}.</p>
 */
public final class CrosshairModule extends QoLModule {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "ui.crosshair";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.ui.crosshair";

    /** Outline colour a fresh install starts with: opaque black. */
    public static final int DEFAULT_OUTLINE_COLOR = 0xFF000000;

    /** Main crosshair colour a fresh install starts with: opaque white. */
    public static final int DEFAULT_COLOR = 0xFFFFFFFF;

    private final Setting<CrosshairStyle> style;
    private final Setting<Integer> color;
    private final Setting<Integer> thickness;
    private final Setting<Integer> gap;
    private final Setting<Integer> length;
    private final Setting<Boolean> outline;
    private final Setting<Integer> outlineColor;

    public CrosshairModule() {
        this.style = Settings.choice("style", CrosshairStyle.CROSS)
                .describedBy(key("style"), key("style") + ".tooltip");
        this.color = Settings.color("color", DEFAULT_COLOR).describedBy(key("color"), null);
        this.thickness = Settings
                .integer("thickness", CrosshairGeometry.DEFAULT_THICKNESS, 1, CrosshairGeometry.MAX_THICKNESS)
                .describedBy(key("thickness"), key("thickness") + ".tooltip");
        this.gap = Settings.integer("gap", CrosshairGeometry.DEFAULT_GAP, 0, CrosshairGeometry.MAX_GAP)
                .describedBy(key("gap"), key("gap") + ".tooltip");
        this.length = Settings
                .integer("length", CrosshairGeometry.DEFAULT_LENGTH, 1, CrosshairGeometry.MAX_LENGTH)
                .describedBy(key("length"), null);
        this.outline = Settings.bool("outline", false).describedBy(key("outline"), null);
        this.outlineColor = Settings.color("outlineColor", DEFAULT_OUTLINE_COLOR)
                .describedBy(key("outlineColor"), null);
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
        return ModuleCategory.INTERFACE;
    }

    @Override
    public void onRegister(ModuleRegistry registry) {
        addSetting(style);
        addSetting(color);
        addSetting(thickness);
        addSetting(gap);
        addSetting(length);
        addSetting(outline);
        addSetting(outlineColor);
    }

    /** @return the shape the current settings describe */
    public CrosshairGeometry geometry() {
        return new CrosshairGeometry(style.get(), thickness.get(), gap.get(), length.get());
    }

    /** @return the colour of every arm and of the dot, ARGB */
    public int color() {
        return color.get();
    }

    /** @return whether a dark border is painted around the shape */
    public boolean outlineEnabled() {
        return outline.get();
    }

    /** @return the colour of that border, ARGB */
    public int outlineColor() {
        return outlineColor.get();
    }

    private static String key(String settingId) {
        return TRANSLATION_KEY + ".setting." + settingId;
    }
}
