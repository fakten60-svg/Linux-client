package wtf.woke.lite.modules.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import wtf.woke.lite.core.Availability;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;
import wtf.woke.lite.hud.HudAnchor;
import wtf.woke.lite.hud.HudBounds;
import wtf.woke.lite.hud.HudContext;
import wtf.woke.lite.hud.HudElement;
import wtf.woke.lite.waypoint.Waypoint;
import wtf.woke.lite.waypoint.WaypointList;
import wtf.woke.lite.waypoint.WaypointResult;
import wtf.woke.lite.waypoint.WaypointWorldKey;
import wtf.woke.lite.waypoint.Waypoints;

/**
 * A list of the player's own positions, pinned to the screen.
 *
 * <p>Read-only and informational: it repeats numbers the player could type out
 * themselves, and it neither reads the world nor talks to the server. Waypoints
 * belong to the world and dimension they were created in, so what is drawn is
 * always the current one.</p>
 *
 * <p>The list itself lives in the config and is edited through the
 * {@code /wokewtf waypoint} client command — which runs entirely on this client,
 * so it never reaches a server. The rules for those edits live in
 * {@link WaypointList} and the naming of a world in {@link WaypointWorldKey};
 * this class only wires them to settings and to the HUD.</p>
 */
public final class WaypointModule extends HudElement {

    /** Stable id; also the key this module and its settings use in the config. */
    public static final String MODULE_ID = "ui.waypoints";

    /** Translation key for the module's display name. */
    public static final String TRANSLATION_KEY = "wokewtf.lite.module.ui.waypoints";

    /** Reason key shown while no world is loaded. */
    public static final String REASON_NO_WORLD = "wokewtf.lite.reason.no_world";

    private static final int DEFAULT_SHOWN = 5;
    private static final int MAX_SHOWN = 16;

    private final Setting<List<String>> entries;
    private final Setting<Integer> maxShown;
    private final Setting<Boolean> showCoordinates;

    public WaypointModule() {
        this.entries = Settings
                .stringList("waypoints", List.of(), WaypointList.MAX_ENTRIES, Waypoint.MAX_ENCODED_LENGTH)
                .describedBy(settingKey("waypoints"), null);
        this.maxShown = Settings.integer("maxShown", DEFAULT_SHOWN, 1, MAX_SHOWN)
                .describedBy(settingKey("maxShown"), null);
        this.showCoordinates = Settings.bool("showCoordinates", false)
                .describedBy(settingKey("showCoordinates"), null);
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
        addLayoutSettings(HudAnchor.TOP_RIGHT, 4, 4);
        addSetting(entries);
        addSetting(maxShown);
        addSetting(showCoordinates);
    }

    /** Without a world there is nothing to measure a waypoint against. */
    @Override
    public Availability availability() {
        return client().world == null
                ? Availability.unavailable(REASON_NO_WORLD)
                : Availability.available();
    }

    // --- lookups and edits, used by the client command ------------------------

    /** @return every stored waypoint, of every world */
    public List<Waypoint> allWaypoints() {
        return WaypointList.all(entries);
    }

    /** @return the waypoints of the world and dimension the player is in */
    public List<Waypoint> waypointsHere() {
        return WaypointWorldKey.current().map(key -> WaypointList.inWorld(entries, key)).orElseGet(List::of);
    }

    /** @return those waypoints' names, for command suggestions */
    public List<String> namesHere() {
        return waypointsHere().stream().map(Waypoint::name).toList();
    }

    /**
     * @param name the name to store the player's current position under
     * @return why the waypoint was or was not stored
     */
    public WaypointResult addHere(String name) {
        if (!Waypoint.isValidName(name)) {
            return WaypointResult.INVALID_NAME;
        }
        ClientPlayerEntity player = client().player;
        if (player == null) {
            return WaypointResult.NO_WORLD;
        }
        return WaypointWorldKey.current()
                .map(key -> WaypointList.add(entries, key, name, floor(player.getX()), floor(player.getY()),
                        floor(player.getZ())))
                .orElse(WaypointResult.NO_WORLD);
    }

    /**
     * @param name the waypoint to drop from the current world
     * @return why the waypoint was or was not removed
     */
    public WaypointResult removeHere(String name) {
        return WaypointWorldKey.current()
                .map(key -> WaypointList.remove(entries, key, name))
                .orElse(WaypointResult.NO_WORLD);
    }

    // --- drawing -------------------------------------------------------------

    @Override
    protected int contentWidth(HudContext context) {
        int widest = 0;
        for (Text line : lines()) {
            widest = Math.max(widest, context.textRenderer().getWidth(line));
        }
        return widest;
    }

    @Override
    protected int contentHeight(HudContext context) {
        return lines().size() * context.lineHeight();
    }

    @Override
    protected void draw(HudContext context, HudBounds screenBounds) {
        List<Text> lines = lines();
        for (int index = 0; index < lines.size(); index++) {
            context.drawContext().drawTextWithShadow(context.textRenderer(), lines.get(index),
                    screenBounds.x(), screenBounds.y() + index * context.lineHeight(), context.textColor());
        }
    }

    private List<Text> lines() {
        ClientPlayerEntity player = client().player;
        if (player == null) {
            return List.of();
        }
        List<Waypoint> here = Waypoints.nearestFirst(waypointsHere(), player.getX(), player.getY(), player.getZ());
        int shown = Math.min(maxShown.get(), here.size());
        List<Text> lines = new ArrayList<>(shown);
        for (Waypoint waypoint : here.subList(0, shown)) {
            long distance = waypoint.distanceFrom(player.getX(), player.getY(), player.getZ());
            lines.add(showCoordinates.get()
                    ? Text.translatable(TRANSLATION_KEY + ".line.withCoordinates", waypoint.name(), distance,
                            waypoint.x(), waypoint.y(), waypoint.z())
                    : Text.translatable(TRANSLATION_KEY + ".line", waypoint.name(), distance));
        }
        return lines;
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private static int floor(double coordinate) {
        return (int) Math.floor(coordinate);
    }
}
