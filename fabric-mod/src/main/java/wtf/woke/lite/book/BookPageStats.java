package wtf.woke.lite.book;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How full one page of a book is.
 *
 * <p>A book page is capped at a fixed number of characters, and the game gives
 * the author no way to see how close a page is to that cap — which is exactly
 * the moment a page silently stops accepting text. This is the arithmetic
 * behind the counter that answers it.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 *
 * @param pageNumber     the page's one-based number
 * @param pageCount      how many pages the book has
 * @param characters     how many characters the page holds
 * @param characterLimit how many the game accepts per page
 */
public record BookPageStats(int pageNumber, int pageCount, int characters, int characterLimit) {

    public BookPageStats {
        requireAtLeast(pageNumber, 1, "pageNumber");
        requireAtLeast(pageCount, pageNumber, "pageCount");
        requireAtLeast(characters, 0, "characters");
        requireAtLeast(characterLimit, 1, "characterLimit");
    }

    /**
     * @param pageIndex      zero-based index of the current page
     * @param pages          the book's pages, as the game holds them
     * @param characterLimit how many characters the game accepts per page
     * @return the stats for that page, or empty when the index names no page
     */
    public static Optional<BookPageStats> of(int pageIndex, List<String> pages, int characterLimit) {
        Objects.requireNonNull(pages, "pages");
        if (pageIndex < 0 || pageIndex >= pages.size()) {
            return Optional.empty();
        }
        String page = pages.get(pageIndex);
        return Optional.of(new BookPageStats(pageIndex + 1, pages.size(), page == null ? 0 : page.length(),
                characterLimit));
    }

    /** @return {@code true} when the page cannot take another character */
    public boolean isFull() {
        return characters >= characterLimit;
    }

    /** @return how many characters still fit, never negative */
    public int remaining() {
        return Math.max(0, characterLimit - characters);
    }

    private static void requireAtLeast(int value, int minimum, String name) {
        if (value < minimum) {
            throw new IllegalArgumentException(name + " must be >= " + minimum + ", got " + value);
        }
    }
}
