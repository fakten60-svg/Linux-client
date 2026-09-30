package wtf.woke.lite.chat;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import wtf.woke.lite.core.Labelled;

/**
 * How a chat timestamp is written.
 *
 * <p>Deliberately limited to the two 24-hour readings a chat line can use
 * without ambiguity: a full date on every message would be noise, and a
 * locale-dependent format would make the same message read differently on two
 * machines. {@link Locale#ROOT} keeps the digits and the padding stable
 * everywhere.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 */
public enum ChatTimestampStyle implements Labelled {

    /** {@code [14:32] } */
    HOUR_MINUTE("HH:mm"),

    /** {@code [14:32:07] } */
    HOUR_MINUTE_SECOND("HH:mm:ss");

    /** Opening bracket of a rendered timestamp. */
    public static final String PREFIX = "[";

    /** Closing bracket and separator of a rendered timestamp. */
    public static final String SUFFIX = "] ";

    private final DateTimeFormatter formatter;

    ChatTimestampStyle(String pattern) {
        this.formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
    }

    /**
     * @param time the local time to render
     * @return the time, zero-padded, on the 24-hour clock
     */
    public String format(LocalTime time) {
        return formatter.format(Objects.requireNonNull(time, "time"));
    }

    /**
     * @param time the local time to render
     * @return the complete chat prefix, e.g. {@code "[14:32] "}
     */
    public String prefix(LocalTime time) {
        return PREFIX + format(time) + SUFFIX;
    }

    /** @return the translation key for this constant's display label */
    public String translationKey() {
        return "wokewtf.lite.chat_timestamp_style." + name().toLowerCase(Locale.ROOT);
    }
}
