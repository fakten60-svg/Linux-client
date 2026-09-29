package wtf.woke.lite.testutil;

import java.util.List;
import wtf.woke.lite.core.Availability;
import wtf.woke.lite.core.ModuleCategory;
import wtf.woke.lite.core.ModuleRegistry;
import wtf.woke.lite.core.QoLModule;
import wtf.woke.lite.core.Setting;
import wtf.woke.lite.core.Settings;

/**
 * Recording {@link QoLModule} used by the framework tests.
 *
 * <p>Counts every lifecycle callback so tests can assert that transitions
 * happen exactly once, and can be switched to throw on demand.</p>
 */
public class FakeModule extends QoLModule {

    /** Enum used by the {@code anchor} setting of {@link #withSettings}. */
    public enum Anchor {
        TOP_LEFT,
        CENTER,
        BOTTOM_RIGHT
    }

    private final String id;
    private final ModuleCategory category;

    private int enableCount;
    private int disableCount;
    private int tickCount;
    private int joinCount;
    private int leaveCount;
    private boolean failOnEnable;
    private boolean failOnTick;
    private Availability availability = Availability.available();

    public FakeModule(String id) {
        this(id, ModuleCategory.HUD);
    }

    public FakeModule(String id, ModuleCategory category) {
        this.id = id;
        this.category = category;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String translationKey() {
        return "test.module." + id;
    }

    @Override
    public ModuleCategory category() {
        return category;
    }

    @Override
    public Availability availability() {
        return availability;
    }

    @Override
    public void onEnable() {
        enableCount++;
        if (failOnEnable) {
            throw new IllegalStateException("planned enable failure");
        }
    }

    @Override
    public void onDisable() {
        disableCount++;
    }

    @Override
    public void onClientTick() {
        tickCount++;
        if (failOnTick) {
            throw new IllegalStateException("planned tick failure");
        }
    }

    @Override
    public void onJoinWorld() {
        joinCount++;
    }

    @Override
    public void onLeaveWorld() {
        leaveCount++;
    }

    public FakeModule failOnEnable() {
        return failOnEnable(true);
    }

    public FakeModule failOnEnable(boolean value) {
        this.failOnEnable = value;
        return this;
    }

    public FakeModule failOnTick() {
        return failOnTick(true);
    }

    public FakeModule failOnTick(boolean value) {
        this.failOnTick = value;
        return this;
    }

    public FakeModule unavailable(String reasonKey) {
        this.availability = Availability.unavailable(reasonKey);
        return this;
    }

    public FakeModule available() {
        this.availability = Availability.available();
        return this;
    }

    public int enableCount() {
        return enableCount;
    }

    public int disableCount() {
        return disableCount;
    }

    public int tickCount() {
        return tickCount;
    }

    public int joinCount() {
        return joinCount;
    }

    public int leaveCount() {
        return leaveCount;
    }

    /**
     * @return a module declaring one setting of every supported type, for
     *         config round-trip tests
     */
    public static FakeModule withSettings(String id) {
        return new FakeModule(id) {
            @Override
            public void onRegister(ModuleRegistry registry) {
                addSetting(Settings.bool("visible", true));
                addSetting(Settings.integer("offsetX", 4, 0, 100));
                addSetting(Settings.decimal("scale", 1.0, 0.5, 3.0));
                addSetting(Settings.text("label", "hello", 16));
                addSetting(Settings.choice("anchor", Anchor.TOP_LEFT));
                addSetting(Settings.color("tint", 0xFFFFFFFF));
                addSetting(Settings.keybind("toggle", "key.keyboard.g"));
                addSetting(Settings.stringList("macros", List.of("hi"), 4, 12));
            }
        };
    }
}
