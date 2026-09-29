package wtf.woke.lite.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import wtf.woke.lite.core.Setting;

/**
 * The stored macros as the config holds them: encoded strings in one setting.
 *
 * <p>Every edit goes through here, so the rules — a usable name, a usable line,
 * a bounded list, and one macro per name — are stated once. The longest encoded
 * entry the setting will keep is derived from the two field limits rather than
 * guessed, because the setting truncates over-long entries rather than
 * reporting them, and a truncated entry is an unreadable one.</p>
 *
 * <p>Slot numbers are simply positions in the stored list, which is what gives
 * the keybinds something fixed to point at: slot {@code n} types the {@code n}th
 * macro, and removing one shifts the rest up.</p>
 */
public final class ChatMacroList {

    /** How many macros may be stored; also how many macro keybinds ship. */
    public static final int MAX_ENTRIES = 20;

    /** Longest encoded entry the setting must be configured to keep. */
    public static final int MAX_ENCODED_LENGTH = ChatMacro.MAX_NAME_LENGTH + 1 + ChatMacro.MAX_TEXT_LENGTH;

    /** What an edit did, so the caller can report it. */
    public enum Outcome {
        /** A new macro was added. */
        STORED,
        /** An existing macro of that name was updated. */
        REPLACED,
        /** The named macro was dropped. */
        REMOVED,
        /** No macro carries that name. */
        NOT_FOUND,
        /** The name was blank, too long, or contained the separator. */
        INVALID_NAME,
        /** The line was blank or too long. */
        INVALID_TEXT,
        /** The list already holds {@link #MAX_ENTRIES} macros. */
        FULL
    }

    private ChatMacroList() {
        throw new AssertionError("No instances of " + ChatMacroList.class.getName());
    }

    /** @return every stored macro, in the order it was added, skipping unreadable entries */
    public static List<ChatMacro> decodeAll(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<ChatMacro> macros = new ArrayList<>(raw.size());
        for (String entry : raw) {
            ChatMacro.decode(entry).ifPresent(macros::add);
        }
        return List.copyOf(macros);
    }

    /** @return the encoded form of every macro, ready for the setting */
    public static List<String> encodeAll(List<ChatMacro> macros) {
        Objects.requireNonNull(macros, "macros");
        return macros.stream().map(ChatMacro::encode).toList();
    }

    /** @return the stored macros of the setting */
    public static List<ChatMacro> all(Setting<List<String>> entries) {
        Objects.requireNonNull(entries, "entries");
        return decodeAll(entries.get());
    }

    /** @return the macro with that name, if any */
    public static Optional<ChatMacro> byName(List<ChatMacro> macros, String name) {
        Objects.requireNonNull(name, "name");
        return macros.stream().filter(macro -> macro.name().equals(name)).findFirst();
    }

    /**
     * @param entries the setting holding the encoded list
     * @param slot    a one-based position
     * @return the macro in that slot, empty when the slot is unused or out of range
     */
    public static Optional<ChatMacro> inSlot(Setting<List<String>> entries, int slot) {
        List<ChatMacro> macros = all(entries);
        if (slot < 1 || slot > macros.size()) {
            return Optional.empty();
        }
        return Optional.of(macros.get(slot - 1));
    }

    /**
     * Adds a macro, or updates the existing one of the same name.
     *
     * @return what was written, or why nothing was
     */
    public static Outcome store(Setting<List<String>> entries, String name, String text) {
        Objects.requireNonNull(entries, "entries");
        if (!ChatMacro.isValidName(name)) {
            return Outcome.INVALID_NAME;
        }
        if (!ChatMacro.isValidText(text)) {
            return Outcome.INVALID_TEXT;
        }
        List<ChatMacro> macros = new ArrayList<>(all(entries));
        ChatMacro macro = new ChatMacro(name.trim(), text);
        for (int index = 0; index < macros.size(); index++) {
            if (macros.get(index).name().equals(macro.name())) {
                macros.set(index, macro);
                entries.set(encodeAll(macros));
                return Outcome.REPLACED;
            }
        }
        if (macros.size() >= MAX_ENTRIES) {
            return Outcome.FULL;
        }
        macros.add(macro);
        entries.set(encodeAll(macros));
        return Outcome.STORED;
    }

    /**
     * Drops the macro with that name.
     *
     * @return {@link Outcome#REMOVED}, or why nothing was written
     */
    public static Outcome remove(Setting<List<String>> entries, String name) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(name, "name");
        List<ChatMacro> macros = new ArrayList<>(all(entries));
        if (!macros.removeIf(macro -> macro.name().equals(name))) {
            return Outcome.NOT_FOUND;
        }
        entries.set(encodeAll(macros));
        return Outcome.REMOVED;
    }
}
