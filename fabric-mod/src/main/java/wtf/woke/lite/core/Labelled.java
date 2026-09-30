package wtf.woke.lite.core;

/**
 * Something that can name itself in the config screen.
 *
 * <p>The enums a setting can hold already carried a {@code translationKey()}
 * each, but nothing said so, which left the screen unable to label a value
 * without knowing which enum it was looking at — a switch over every enum in
 * the project, in a screen. Naming the contract once lets the screen ask any
 * {@link SettingType#ENUM} value for its key, and lets a test assert that every
 * constant a player can pick has English text.</p>
 *
 * <p>The key is returned rather than the resolved text so that this stays
 * independent of the game, exactly like the enums themselves.</p>
 */
public interface Labelled {

    /** @return the translation key of this value's label */
    String translationKey();
}
