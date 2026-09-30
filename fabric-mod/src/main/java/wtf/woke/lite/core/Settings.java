package wtf.woke.lite.core;

import java.util.List;
import java.util.Objects;

/**
 * Factories for every kind of {@link Setting}.
 *
 * <p>Split out of {@link Setting} so the value class stays small, and so the
 * full set of supported types can be read at a glance. Each factory pairs the
 * type with the validation that type should always carry.</p>
 */
public final class Settings {

    private Settings() {
        throw new AssertionError("No instances of " + Settings.class.getName());
    }

    /** Boolean switch. */
    public static Setting<Boolean> bool(String id, boolean defaultValue) {
        return new Setting<>(id, SettingType.BOOLEAN, defaultValue);
    }

    /**
     * Integer clamped to {@code [min, max]}.
     *
     * <p>The bounds are also recorded on the setting, so the config screen's
     * slider and the validator agree on what the range is.</p>
     */
    public static Setting<Integer> integer(String id, int defaultValue, int min, int max) {
        return new Setting<Integer>(id, SettingType.INT, defaultValue)
                .validatedBy(Validator.clampInt(min, max))
                .rangedBy(min, max);
    }

    /** Decimal clamped to {@code [min, max]}, with the same bounds recorded. */
    public static Setting<Double> decimal(String id, double defaultValue, double min, double max) {
        return new Setting<Double>(id, SettingType.DOUBLE, defaultValue)
                .validatedBy(Validator.clampDouble(min, max))
                .rangedBy(min, max);
    }

    /** Free text capped at {@code maxLength} characters. */
    public static Setting<String> text(String id, String defaultValue, int maxLength) {
        return new Setting<String>(id, SettingType.STRING, defaultValue).validatedBy(Validator.limitLength(maxLength));
    }

    /** One constant of {@code defaultConstant}'s enum type. */
    public static <E extends Enum<E>> Setting<E> choice(String id, E defaultConstant) {
        Objects.requireNonNull(defaultConstant, "defaultConstant");
        return new Setting<E>(id, SettingType.ENUM, defaultConstant)
                .withEnumType(defaultConstant.getDeclaringClass())
                .validatedBy(candidate -> candidate == null ? defaultConstant : candidate);
    }

    /** Packed ARGB colour, stored as a signed int. */
    public static Setting<Integer> color(String id, int argb) {
        return new Setting<>(id, SettingType.COLOR_ARGB, argb);
    }

    /** GLFW key name; blank input keeps the current binding. */
    public static Setting<String> keybind(String id, String defaultKey) {
        String fallback = defaultKey == null || defaultKey.isBlank() ? KeybindAction.UNBOUND : defaultKey;
        return new Setting<String>(id, SettingType.KEYBIND, fallback)
                .validatedBy(candidate -> candidate == null || candidate.isBlank() ? null : candidate);
    }

    /** Ordered list of short strings. */
    public static Setting<List<String>> stringList(String id, List<String> defaultValue, int maxEntries, int maxLength) {
        Validator<List<String>> validator = Validator.limitList(maxEntries, maxLength);
        List<String> fallback = validator.sanitize(Objects.requireNonNull(defaultValue, "defaultValue"));
        if (fallback == null) {
            throw new IllegalArgumentException("defaultValue rejected by its own limits: " + id);
        }
        return new Setting<List<String>>(id, SettingType.STRING_LIST, fallback).validatedBy(validator);
    }
}
