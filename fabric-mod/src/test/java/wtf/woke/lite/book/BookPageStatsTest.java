package wtf.woke.lite.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookPageStatsTest {

    private static final int LIMIT = 1024;

    @Test
    void ofDescribesThePageItIsPointedAt() {
        List<String> pages = List.of("hello", "a longer second page", "third");

        BookPageStats stats = BookPageStats.of(1, pages, LIMIT).orElseThrow();
        assertEquals(2, stats.pageNumber(), "page numbers are shown to the reader, so they are one-based");
        assertEquals(3, stats.pageCount());
        assertEquals("a longer second page".length(), stats.characters());
        assertEquals(LIMIT, stats.characterLimit());
        assertFalse(stats.isFull());
    }

    @Test
    void ofRejectsAnyIndexThatNamesNoPage() {
        List<String> pages = List.of("only page");

        assertTrue(BookPageStats.of(-1, pages, LIMIT).isEmpty());
        assertTrue(BookPageStats.of(1, pages, LIMIT).isEmpty());
        assertTrue(BookPageStats.of(0, List.of(), LIMIT).isEmpty());
        assertThrows(NullPointerException.class, () -> BookPageStats.of(0, null, LIMIT));
    }

    @Test
    void aMissingPageBodyCountsAsZeroCharacters() {
        List<String> pages = new ArrayList<>();
        pages.add(null);

        BookPageStats stats = BookPageStats.of(0, pages, LIMIT).orElseThrow();
        assertEquals(0, stats.characters());
        assertFalse(stats.isFull());
    }

    @Test
    void fullnessIsReachedAtTheLimitAndNeverBelowIt() {
        assertTrue(new BookPageStats(1, 1, LIMIT, LIMIT).isFull());
        assertTrue(new BookPageStats(1, 1, LIMIT + 50, LIMIT).isFull(), "a longer page than the cap still counts");
        assertFalse(new BookPageStats(1, 1, LIMIT - 1, LIMIT).isFull());
    }

    @Test
    void remainingCountsDownAndStopsAtZero() {
        assertEquals(1024, new BookPageStats(1, 1, 0, LIMIT).remaining());
        assertEquals(640, new BookPageStats(1, 1, 384, LIMIT).remaining());
        assertEquals(1, new BookPageStats(1, 1, LIMIT - 1, LIMIT).remaining());
        assertEquals(0, new BookPageStats(1, 1, LIMIT + 10, LIMIT).remaining(),
                "an over-full page has nothing left, not a negative amount");
    }

    @Test
    void inconsistentNumbersAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new BookPageStats(0, 1, 0, LIMIT));
        assertThrows(IllegalArgumentException.class, () -> new BookPageStats(2, 1, 0, LIMIT));
        assertThrows(IllegalArgumentException.class, () -> new BookPageStats(1, 1, -1, LIMIT));
        assertThrows(IllegalArgumentException.class, () -> new BookPageStats(1, 1, 0, 0));
    }

    @Test
    void ofReportsEveryPageOfABook() {
        List<String> pages = Arrays.asList("a", "bb", "ccc");

        for (int index = 0; index < pages.size(); index++) {
            BookPageStats stats = BookPageStats.of(index, pages, LIMIT).orElseThrow();
            assertEquals(index + 1, stats.pageNumber());
            assertEquals(index + 1, stats.characters());
            assertEquals(3, stats.pageCount());
        }
    }
}
