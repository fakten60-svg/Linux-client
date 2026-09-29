package wtf.woke.lite.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import wtf.woke.lite.WokeLite;

/**
 * Reads and writes the config file without ever leaving a half-written file
 * behind.
 *
 * <p>Writing goes to a sibling {@code .tmp} file that is flushed to disk and
 * then moved over the target, so a crash mid-write loses at most the newest
 * change — the previous contents survive as a single-generation {@code .bak}.
 * A file that cannot be parsed is quarantined rather than deleted, because
 * "the game silently reset my settings" is far worse than one stray file.</p>
 */
public final class ConfigIO {

    /** Outcome of an attempted read. */
    public enum Status {
        /** Parsed successfully. */
        OK,
        /** No file yet; first run. */
        MISSING,
        /** Present but not parseable; it has been quarantined. */
        CORRUPT,
        /** Present but unreadable (permissions, I/O); left untouched. */
        UNREADABLE
    }

    /**
     * @param status      what happened
     * @param root        the parsed root object when {@code OK}, else {@code null}
     * @param quarantined where a corrupt file was moved to, when quarantined
     */
    public record ReadResult(Status status, JsonObject root, Path quarantined) {

        /** @return {@code true} when the file was read and parsed */
        public boolean isOk() {
            return status == Status.OK;
        }
    }

    public static final String DEFAULT_FILE_NAME = "wokewtf-lite.json";

    private static final String TEMP_SUFFIX = ".tmp";
    private static final String BACKUP_SUFFIX = ".bak";
    private static final String CORRUPT_SUFFIX = ".corrupt-";

    private final Path directory;
    private final String fileName;
    private final Gson gson;

    public ConfigIO(Path directory) {
        this(directory, DEFAULT_FILE_NAME);
    }

    public ConfigIO(Path directory, String fileName) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public Path directory() {
        return directory;
    }

    /** @return the config file path, whether or not it exists yet */
    public Path path() {
        return directory.resolve(fileName);
    }

    /** @return the single-generation backup path */
    public Path backupPath() {
        return directory.resolve(fileName + BACKUP_SUFFIX);
    }

    /** Reads the file, quarantining it if it cannot be parsed. */
    public ReadResult read() {
        Path file = path();
        if (!Files.isRegularFile(file)) {
            return new ReadResult(Status.MISSING, null, null);
        }

        final String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            WokeLite.LOGGER.error("Config file {} exists but could not be read", file, failure);
            return new ReadResult(Status.UNREADABLE, null, null);
        }

        try {
            JsonElement parsed = JsonParser.parseString(content);
            if (!parsed.isJsonObject()) {
                return quarantine(file, "root element is not a JSON object");
            }
            return new ReadResult(Status.OK, parsed.getAsJsonObject(), null);
        } catch (JsonParseException failure) {
            return quarantine(file, failure.getMessage());
        }
    }

    /**
     * Serialises {@code root} and replaces the config file atomically.
     *
     * @return {@code false} when the write failed; the previous file is intact
     */
    public boolean write(JsonObject root) {
        Objects.requireNonNull(root, "root");
        Path file = path();
        Path temp = directory.resolve(fileName + TEMP_SUFFIX);
        try {
            Files.createDirectories(directory);
            byte[] bytes = (gson.toJson(root) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            keepBackup(file);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException failure) {
            WokeLite.LOGGER.error("Failed to write config file {}", file, failure);
            return false;
        }
    }

    private ReadResult quarantine(Path file, String reason) {
        Path target = uniqueQuarantinePath(System.currentTimeMillis());
        WokeLite.LOGGER.warn("Config file {} could not be parsed ({}); moving it to {}", file, reason, target.getFileName());
        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            return new ReadResult(Status.CORRUPT, null, target);
        } catch (IOException failure) {
            WokeLite.LOGGER.error("Could not quarantine {}; continuing with defaults", file, failure);
            return new ReadResult(Status.CORRUPT, null, null);
        }
    }

    private Path uniqueQuarantinePath(long stamp) {
        Path candidate = directory.resolve(fileName + CORRUPT_SUFFIX + stamp);
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(fileName + CORRUPT_SUFFIX + stamp + "-" + counter++);
        }
        return candidate;
    }

    private void keepBackup(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Files.copy(file, backupPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException failure) {
            WokeLite.LOGGER.warn("Could not refresh config backup {}", backupPath(), failure);
        }
    }
}
