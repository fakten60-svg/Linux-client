package wtf.woke.lite.screenshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import wtf.woke.lite.WokeLite;

/**
 * Reports screenshots the module has not seen before.
 *
 * <p>Two sources feed it. {@link #offer(Path)} takes the exact file the game
 * says it wrote — the one moment where the file is known for certain rather than
 * guessed — and {@link #poll()} scans the directory for anything that arrived
 * another way, such as a screenshot taken by a mod that never goes through the
 * game's own code. A file is reported once, whichever source saw it first.</p>
 *
 * <p>A file name is reported once per watcher instance. {@link #prime()} exists
 * for the restart case: without it, the newest screenshot on disk would be
 * "discovered" again every time the game starts.</p>
 *
 * <p>The game reports its screenshots from the thread that writes the file, so
 * the bookkeeping is guarded; the directory listing stays outside the lock, so a
 * poll never holds up that writer.</p>
 *
 * <p>No Minecraft types, so it is unit-testable without a game.</p>
 */
public final class ScreenshotWatcher {

    /** Extension the game writes screenshots with. */
    public static final String EXTENSION = ".png";

    private final Path directory;
    private final Path absoluteDirectory;
    private final Set<String> knownNames = new HashSet<>();
    private final Deque<Path> offered = new ArrayDeque<>();

    /**
     * @param directory the directory to watch; it does not have to exist yet
     */
    public ScreenshotWatcher(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.absoluteDirectory = directory.toAbsolutePath().normalize();
    }

    /** Records the screenshots that are already there, so they are not reported. */
    public void prime() {
        List<Path> existing = screenshots();
        synchronized (this) {
            existing.forEach(file -> knownNames.add(file.getFileName().toString()));
        }
    }

    /**
     * Records a screenshot the game reported itself.
     *
     * @param file the file the game just wrote
     * @return {@code true} when this file is new to the watcher; anything that
     *         does not sit directly in the watched directory is refused, so a
     *         name that wandered out of it can never be announced
     */
    public boolean offer(Path file) {
        Objects.requireNonNull(file, "file");
        Path candidate = file.toAbsolutePath().normalize();
        if (!absoluteDirectory.equals(candidate.getParent())) {
            return false;
        }
        synchronized (this) {
            if (!knownNames.add(candidate.getFileName().toString())) {
                return false;
            }
            offered.add(candidate);
            return true;
        }
    }

    /**
     * @return the files offered since the last call, in the order they arrived;
     *         this does not touch the file system, so it can be asked often
     */
    public List<Path> takeOffered() {
        synchronized (this) {
            if (offered.isEmpty()) {
                return List.of();
            }
            List<Path> fresh = List.copyOf(offered);
            offered.clear();
            return fresh;
        }
    }

    /**
     * @return the screenshots that appeared in the directory since the last
     *         call, in name order; the game names them after the capture time,
     *         so that is also chronological order
     */
    public List<Path> poll() {
        List<Path> fresh = new ArrayList<>();
        for (Path file : screenshots()) {
            synchronized (this) {
                if (knownNames.add(file.getFileName().toString())) {
                    fresh.add(file);
                }
            }
        }
        fresh.sort(Comparator.comparing(Path::toString));
        return List.copyOf(fresh);
    }

    /** @return how many file names this watcher has seen so far */
    public int knownCount() {
        synchronized (this) {
            return knownNames.size();
        }
    }

    private List<Path> screenshots() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(ScreenshotWatcher::isScreenshot)
                    .toList();
        } catch (IOException failure) {
            WokeLite.LOGGER.warn("Could not list the screenshot directory {}", directory, failure);
            return List.of();
        }
    }

    private static boolean isScreenshot(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }
}
