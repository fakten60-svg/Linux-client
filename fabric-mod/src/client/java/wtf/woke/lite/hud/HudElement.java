package wtf.woke.lite.hud;

import wtf.woke.lite.WokeLite;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;

/**
 * A drawn HUD readout: measures itself, is placed by its anchor, then draws.
 *
 * <p>Subclasses only implement {@link #contentWidth}, {@link #contentHeight}
 * and {@link #draw}. Everything else — the anchor/offset settings, the
 * placement arithmetic, the backing plate and the diagnostic log — is shared, so
 * a new readout is three small methods and one registration line.</p>
 *
 * <p>Placement is re-derived when a setting changes, never inside the draw call,
 * so the per-frame cost is one {@link HudLayout#bounds} call.</p>
 */
public abstract class HudElement extends QoLModule {

    /** Gap between an element's content and its backing plate. */
    private static final int PLATE_PADDING = 2;

    private HudLayout layout = new HudLayout(HudAnchor.TOP_LEFT, 0, 0);
    private HudBounds lastBounds = HudBounds.sized(0, 0);

    private Setting<HudAnchor> anchorSetting;
    private Setting<Integer> offsetXSetting;
    private Setting<Integer> offsetYSetting;

    /**
     * Declares the anchor and offset settings. Call from {@link #onRegister},
     * before adding any of the element's own settings.
     *
     * @param defaultAnchor  anchor a fresh install starts with
     * @param defaultOffsetX inward offset from that anchor's edge
     * @param defaultOffsetY inward offset from that anchor's edge
     */
    protected final void addLayoutSettings(HudAnchor defaultAnchor, int defaultOffsetX, int defaultOffsetY) {
        String prefix = translationKey() + ".setting.";
        anchorSetting = Settings.choice("anchor", defaultAnchor).describedBy(prefix + "anchor", prefix + "anchor.tooltip");
        offsetXSetting = Settings.integer("offsetX", defaultOffsetX, 0, 4096).describedBy(prefix + "offsetX", null);
        offsetYSetting = Settings.integer("offsetY", defaultOffsetY, 0, 4096).describedBy(prefix + "offsetY", null);
        addSetting(anchorSetting);
        addSetting(offsetXSetting);
        addSetting(offsetYSetting);
        refreshLayout();
    }

    /** Generates a description key for one of this element's own settings. */
    protected final String settingKey(String settingId) {
        return translationKey() + ".setting." + settingId;
    }

    /** @return the element's width in GUI units */
    protected abstract int contentWidth(HudContext context);

    /** @return the element's height in GUI units */
    protected abstract int contentHeight(HudContext context);

    /**
     * Draws the element inside {@code screenBounds}.
     *
     * <p>The bounds are absolute GUI coordinates, so text is drawn at
     * {@code screenBounds.x()} and {@code screenBounds.y()}.</p>
     */
    protected abstract void draw(HudContext context, HudBounds screenBounds);

    /** @return the current placement rule */
    public final HudLayout layout() {
        return layout;
    }

    /** @return where this element was placed on the last rendered frame */
    public final HudBounds lastBounds() {
        return lastBounds;
    }

    /**
     * Measures, places and draws this element. Called by {@link HudRenderer}
     * for every active element, every frame.
     */
    public final void renderHud(HudContext context) {
        int width = Math.max(0, contentWidth(context));
        int height = Math.max(0, contentHeight(context));
        HudBounds bounds = layout.bounds(width, height, context.screenWidth(), context.screenHeight());
        if (!bounds.equals(lastBounds)) {
            WokeLite.LOGGER.info("HUD element '{}' placed at ({}, {}), {}x{} in a {}x{} screen",
                    id(), bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    context.screenWidth(), context.screenHeight());
            lastBounds = bounds;
        }

        if (context.backgroundEnabled()) {
            HudBounds plate = bounds.expanded(PLATE_PADDING);
            context.drawContext().fill(plate.x(), plate.y(), plate.right(), plate.bottom(), context.backgroundColor());
        }
        draw(context, bounds);
    }

    @Override
    public void onSettingChanged(Setting<?> setting) {
        refreshLayout();
        super.onSettingChanged(setting);
    }

    private void refreshLayout() {
        if (anchorSetting == null || offsetXSetting == null || offsetYSetting == null) {
            return;
        }
        layout = new HudLayout(anchorSetting.get(), offsetXSetting.get(), offsetYSetting.get());
    }
}
