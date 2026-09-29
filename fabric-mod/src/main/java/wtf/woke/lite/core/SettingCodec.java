package wtf.woke.lite.core;

import java.util.List;
import java.util.Objects;
import wtf.woke.lite.WokeLite;

/**
 * Bridges settings to and from plain Java values.
 *
 * <p>The config layer speaks JSON, the framework speaks Java, and neither
 * needs to know about the other: this class is the only place that knows how
 * each {@link SettingType} crosses that boundary. It is package-private because
 * it is an implementation detail of {@link Setting}.</p>
 */
final class SettingCodec {

    private SettingCodec() {
        throw new AssertionError("No instances of " + SettingCodec.class.getName());
    }

    /**
     * @return a JSON-friendly value: {@code Boolean}, {@code Integer},
     *         {@code Double}, {@code String} or {@code List<String>}
     */
    static Object toRaw(Setting<?> setting) {
        Object value = setting.get();
        return switch (setting.type()) {
            case BOOLEAN, INT, COLOR_ARGB, DOUBLE -> value;
            case STRING, KEYBIND -> String.valueOf(value);
            case ENUM -> ((Enum<?>) value).name();
            case STRING_LIST -> List.copyOf(asStringList(value));
        };
    }

    /**
     * Applies a decoded value. Wrong types and unknown enum constants are
     * ignored rather than guessed at, so a hand-edited config file cannot
     * corrupt a setting.
     *
     * @return {@code true} when the stored value actually changed
     */
    static <T> boolean fromRaw(Setting<T> setting, Object raw) {
        if (raw == null) {
            return false;
        }
        return switch (setting.type()) {
            case BOOLEAN -> raw instanceof Boolean stored && setting.set(cast(stored));
            case INT, COLOR_ARGB -> raw instanceof Number number && setting.set(cast(number.intValue()));
            case DOUBLE -> raw instanceof Number number && setting.set(cast(number.doubleValue()));
            case STRING, KEYBIND -> raw instanceof String stored && setting.set(cast(stored));
            case ENUM -> raw instanceof String name && decodeEnum(setting, name);
            case STRING_LIST -> raw instanceof List<?> list && setting.set(copyOfList(list));
        };
    }

    /** Copies a list into an immutable, null-free list of strings. */
    static <T> T copyOfList(List<?> list) {
        return cast(list.stream().filter(Objects::nonNull).map(String::valueOf).toList());
    }

    private static <T> boolean decodeEnum(Setting<T> setting, String name) {
        for (Enum<?> constant : setting.enumConstants()) {
            if (constant.name().equalsIgnoreCase(name)) {
                return setting.set(cast(constant));
            }
        }
        WokeLite.LOGGER.warn("Ignoring unknown value '{}' for setting '{}'", name, setting.id());
        return false;
    }

    private static List<String> asStringList(Object candidate) {
        return candidate instanceof List<?> list ? copyOfList(list) : List.of();
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object candidate) {
        return (T) candidate;
    }
}
