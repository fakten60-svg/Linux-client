package wtf.woke.lite.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreenshotWatcherTest {

    @TempDir
    Path directory;

    @Test
    void primeRecordsWhatIsAlreadyThereWithoutReportingIt() throws IOException {
        write("2026-01-01_10-00-00.png");
        ScreenshotWatcher watcher = new ScreenshotWatcher(directory);

        watcher.prime();

        assertEquals(List.of(), watcher.poll(), "a restart must not re-announce the previous session's shots");
        assertEquals(1, watcher.knownCount());
    }

    @Test
    void pollReportsEachNewScreenshotExactlyOnce() throws IOException {
        ScreenshotWatcher watcher = new ScreenshotWatcher(directory);
        watcher.prime();

        Path shot = write("2026-01-01_10-00-00.png");
        assertEquals(List.of(shot), watcher.poll());
        assertEquals(List.of(), watcher.poll(), "the same file must not be copied twice");
        assertEquals(1, watcher.knownCount());
    }

    @Test
    void severalNewScreenshotsComeBackOldestFirst() throws IOException {
        ScreenshotWatcher watcher = new ScreenshotWatcher(directory);
        watcher.prime();

        Path later = write("2026-01-01_11-00-00.png");
        Path earlier = write("2026-01-01_10-00-00.png");

        assertEquals(List.of(earlier, later), watcher.poll(),
                "the game names screenshots after the capture time, so name order is capture order");
    }

    @Test
    void onlyPngFilesCount() throws IOException {
        ScreenshotWatcher watcher = new ScreenshotWatcher(directory);
        watcher.prime();

        write("notes.txt");
        write("image.PNG");
        Files.createDirectory(directory.resolve("subdirectory"));

        assertEquals(List.of(directory.resolve("image.PNG")), watcher.poll());
    }

    @Test
    void aMissingDirectoryIsNotAnError() {
        ScreenshotWatcher watcher = new ScreenshotWatcher(directory.resolve("not-there"));

        watcher.prime();

        assertEquals(List.of(), watcher.poll());
        assertTrue(Files.notExists(directory.resolve("not-there")), "watching must not create the directory");
    }

    @Test
    void onlyTheWatchedDirectoryIsListed() throws IOException {
        Path other = Files.createDirectory(directory.resolve("other"));
        ScreenshotWatcher watcher = new ScreenshotWatcher(other);
        watcher.prime();

        write("2026-01-01_10-00-00.png");
        assertEquals(List.of(), watcher.poll(), "this shot is not in the watched directory");

        Path watched = write(other, "2026-01-01_12-00-00.png");
        assertEquals(List.of(watched), watcher.poll());
    }

    @Test
    void anOfferedFileIsReportedAtOnceAndNeverByTheScan() throws IOException {
        ScreenshotWatcher watcher = new ScreenshotWatcher(directory);
        watcher.prime();
        Path shot = write("2026-01-01_10-00-00.png");

        assertTrue(watcher.offer(shot), "the game's own report is news to the watcher");
        assertEquals(List.of(shot.toAbsolutePath().normalize()), watcher.takeOffered());
        assertEquals(List.of(), watcher.takeOffered(), "taking the offered files drains them");
        assertEquals(List.of(), watcher.poll(), "the scan must not announce the same file again");
        assertFalse(watcher.offer(shot), "the same file is not news twice");
    }

    @Test
    void theScanStillFindsFilesTheGameDidNotReport() throws IOException {
        ScreenshotWatcher watcher = new ScreenshotWatcher(directory);
        watcher.prime();

        Path reported = write("2026-01-01_10-00-00.png");
        Path otherMod = write("2026-01-01_11-00-00.png");
        watcher.offer(reported);

        assertEquals(List.of(otherMod), watcher.poll(), "both sources feed the same list of new files");
        assertEquals(2, watcher.knownCount());
    }

    @Test
    void filesOutsideTheWatchedDirectoryAreRefused() {
        ScreenshotWatcher watcher = new ScreenshotWatcher(directory);

        assertFalse(watcher.offer(directory.getParent().resolve("elsewhere.png")),
                "only files in the watched directory may be announced");
        assertFalse(watcher.offer(directory.resolve("nested").resolve("deep.png")),
                "a subdirectory is not the screenshot folder");
        assertEquals(List.of(), watcher.takeOffered());
        assertEquals(0, watcher.knownCount());
    }

    private Path write(String name) throws IOException {
        return write(directory, name);
    }

    private static Path write(Path target, String name) throws IOException {
        return Files.writeString(target.resolve(name), "png", StandardCharsets.UTF_8);
    }
}
