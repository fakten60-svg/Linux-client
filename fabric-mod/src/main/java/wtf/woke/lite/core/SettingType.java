package wtf.woke.lite.core;

/**
 * The value kinds a {@link Setting} can hold.
 *
 * <p>Drives three things at once: which config-screen widget the setting gets,
 * how it is written to JSON, and how a JSON value is decoded again. Keeping it
 * a closed set means an unknown type cannot silently appear in a config
 * file.</p>
 */
public enum SettingType {

    /** On/off switch. */
    BOOLEAN,

    /** Whole number inside a fixed range. */
    INT,

    /** Decimal number inside a fixed range. */
    DOUBLE,

    /** Free text with a length cap. */
    STRING,

    /** One constant of a Java enum. */
    ENUM,

    /** Packed ARGB colour, stored as a signed 32-bit int. */
    COLOR_ARGB,

    /** GLFW key name, e.g. {@code key.keyboard.right.shift}. */
    KEYBIND,

    /** Ordered list of short strings. */
    STRING_LIST
}
