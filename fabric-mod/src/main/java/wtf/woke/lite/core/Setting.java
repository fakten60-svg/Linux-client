package wtf.woke.lite.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import wtf.woke.lite.WokeLite;

/**
 * One configurable value belonging to a module or to the mod as a whole.
 *
 * <p>A setting owns its value, its default, its validation and its observers.
 * Value conversion lives in {@link SettingCodec}, so this class holds no
 * serialisation format and needs neither a display nor a game to be exercised.
 * Instances are created through {@link Settings}.</p>
 *
 * @param <T> the stored value type
 */
public final class Setting<T> {

    private static final String LABEL_PREFIX = "wokewtf.lite.setting.";

    private final String id;
    private final SettingType type;
    private final T defaultValue;
    private final List<Runnable> listeners = new ArrayList<>(2);

    private T value;
    private Validator<T> validator;
    private Consumer<T> changeListener;
    private String translationKey;
    private String descriptionKey;
    private Class<? extends Enum<?>> enumType;
    private double rangeMin;
    private double rangeMax;

    Setting(String id, SettingType type, T defaultValue) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("setting id must not be blank");
        }
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.value = defaultValue;
        this.translationKey = LABEL_PREFIX + id;
    }

    /** Overrides the automatic translation key. */
    public Setting<T> describedBy(String labelKey, String descriptionKey) {
        this.translationKey = Objects.requireNonNull(labelKey, "labelKey");
        this.descriptionKey = descriptionKey;
        return this;
    }

    /** Installs (or replaces) the value validator. */
    public Setting<T> validatedBy(Validator<T> newValidator) {
        this.validator = Objects.requireNonNull(newValidator, "validator");
        return this;
    }

    /** Convenience reaction fired when the value changes. */
    public Setting<T> onChanged(Consumer<T> listener) {
        this.changeListener = Objects.requireNonNull(listener, "listener");
        return this;
    }

    /** Registers a change observer, used to mark the config dirty. */
    public Setting<T> addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    public String id() {
        return id;
    }

    public SettingType type() {
        return type;
    }

    public T get() {
        return value;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public boolean isDefault() {
        return Objects.equals(value, defaultValue);
    }

    public String translationKey() {
        return translationKey;
    }

    /** @return the tooltip translation key, empty when the setting has none */
    public Optional<String> descriptionKey() {
        return Optional.ofNullable(descriptionKey);
    }

    /**
     * Validates and stores {@code candidate}; a rejected candidate falls back
     * to the default.
     *
     * <p>Observers run after the value has been stored, and one throwing
     * observer cannot stop the others or leave the setting inconsistent.</p>
     *
     * @return {@code true} when the stored value actually changed
     */
    public boolean set(T candidate) {
        T sanitized = validator == null ? candidate : validator.sanitize(candidate);
        if (sanitized == null) {
            sanitized = defaultValue;
        }
        if (type == SettingType.STRING_LIST && sanitized instanceof List<?> list) {
            sanitized = SettingCodec.copyOfList(list);
        }
        if (Objects.equals(value, sanitized)) {
            return false;
        }
        value = sanitized;
        notifyObservers();
        return true;
    }

    /** Restores the default value. @return {@code true} when something changed */
    public boolean reset() {
        return set(defaultValue);
    }

    /** @return a JSON-friendly representation of the current value */
    public Object toRaw() {
        return SettingCodec.toRaw(this);
    }

    /** @return {@code true} when the decoded value changed the stored one */
    public boolean fromRaw(Object raw) {
        return SettingCodec.fromRaw(this, raw);
    }

    /** @return the enum constants of an {@code ENUM} setting, else an empty array */
    public Enum<?>[] enumConstants() {
        return enumType == null ? new Enum<?>[0] : enumType.getEnumConstants();
    }

    /**
     * Records the bounds a config-screen slider may pick from.
     *
     * <p>Stored rather than derived: the validator clamps into the range but does
     * not expose it, so a slider that guessed would let a value be dragged past
     * what the validator keeps.</p>
     */
    Setting<T> rangedBy(double min, double max) {
        this.rangeMin = min;
        this.rangeMax = max;
        return this;
    }

    /** @return the lowest value a slider may pick */
    public double rangeMin() {
        return rangeMin;
    }

    /** @return the highest value a slider may pick */
    public double rangeMax() {
        return rangeMax;
    }

    /** Records the enum type so config decoding can resolve constants by name. */
    Setting<T> withEnumType(Class<? extends Enum<?>> newEnumType) {
        this.enumType = newEnumType;
        return this;
    }

    private void notifyObservers() {
        if (changeListener != null) {
            try {
                changeListener.accept(value);
            } catch (Exception failure) {
                WokeLite.LOGGER.error("Setting '{}' change listener threw", id, failure);
            }
        }
        for (Runnable listener : List.copyOf(listeners)) {
            try {
                listener.run();
            } catch (Exception failure) {
                WokeLite.LOGGER.error("Setting '{}' observer threw", id, failure);
            }
        }
    }

    @Override
    public String toString() {
        return id + "(" + type + ")=" + toRaw();
    }
}
