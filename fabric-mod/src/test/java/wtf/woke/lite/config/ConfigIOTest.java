package wtf.woke.lite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigIOTest {

    @TempDir
    Path tempDir;

    private static JsonObject sample(String marker) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("marker", marker);
        return root;
    }

    private static List<Path> quarantinedFiles(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(path -> path.getFileName().toString().contains(".corrupt-")).toList();
        }
    }

    @Test
    void aMissingFileIsReportedAndNotCreated() {
        ConfigIO io = new ConfigIO(tempDir);

        ConfigIO.ReadResult result = io.read();

        assertEquals(ConfigIO.Status.MISSING, result.status());
        assertNull(result.root());
        assertFalse(Files.exists(io.path()));
    }

    @Test
    void writeThenReadRoundTrips() throws IOException {
        ConfigIO io = new ConfigIO(tempDir);

        assertTrue(io.write(sample("first")));

        ConfigIO.ReadResult result = io.read();
        assertEquals(ConfigIO.Status.OK, result.status());
        assertEquals("first", result.root().get("marker").getAsString());

        String content = Files.readString(io.path(), StandardCharsets.UTF_8);
        assertTrue(content.contains(System.lineSeparator()), "output is pretty printed");
    }

    @Test
    void writingLeavesNoTemporaryFileBehind() {
        ConfigIO io = new ConfigIO(tempDir);
        io.write(sample("first"));
        io.write(sample("second"));

        assertFalse(Files.exists(tempDir.resolve(ConfigIO.DEFAULT_FILE_NAME + ".tmp")));
    }

    @Test
    void writingKeepsExactlyOneBackupGeneration() throws IOException {
        ConfigIO io = new ConfigIO(tempDir);
        io.write(sample("first"));
        assertFalse(Files.exists(io.backupPath()), "no backup until something is replaced");

        io.write(sample("second"));

        assertTrue(Files.exists(io.backupPath()));
        assertEquals("first", readJson(io.backupPath()).get("marker").getAsString());
        assertEquals("second", readJson(io.path()).get("marker").getAsString());

        io.write(sample("third"));
        assertEquals("second", readJson(io.backupPath()).get("marker").getAsString());
    }

    @Test
    void creatingMissingDirectoriesIsPartOfTheWrite() {
        ConfigIO io = new ConfigIO(tempDir.resolve("nested").resolve("config"));

        assertTrue(io.write(sample("first")));
        assertTrue(Files.isRegularFile(io.path()));
    }

    @Test
    void corruptJsonIsQuarantinedAndReported() throws IOException {
        ConfigIO io = new ConfigIO(tempDir);
        Files.writeString(io.path(), "{\"a\": [1,}", StandardCharsets.UTF_8);

        ConfigIO.ReadResult result = io.read();

        assertEquals(ConfigIO.Status.CORRUPT, result.status());
        assertNull(result.root());
        assertNotNull(result.quarantined());
        assertTrue(Files.exists(result.quarantined()), "the bad file is kept, not deleted");
        assertFalse(Files.exists(io.path()), "and it is moved out of the way");
    }

    @Test
    void aNonObjectRootIsAlsoQuarantined() throws IOException {
        ConfigIO io = new ConfigIO(tempDir);
        Files.writeString(io.path(), "[1, 2, 3]", StandardCharsets.UTF_8);

        ConfigIO.ReadResult result = io.read();

        assertEquals(ConfigIO.Status.CORRUPT, result.status());
        assertEquals(1, quarantinedFiles(tempDir).size());
    }

    @Test
    void everyCorruptFileIsKeptSeparately() throws IOException {
        ConfigIO io = new ConfigIO(tempDir);
        Files.writeString(io.path(), "{\"a\": [1,}", StandardCharsets.UTF_8);
        assertEquals(ConfigIO.Status.CORRUPT, io.read().status());
        Files.writeString(io.path(), "{\"b\": [2,}", StandardCharsets.UTF_8);
        assertEquals(ConfigIO.Status.CORRUPT, io.read().status());

        assertEquals(2, quarantinedFiles(tempDir).size());
    }

    @Test
    void aWriteThatCannotSucceedIsReportedInsteadOfThrown() throws IOException {
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "not a directory", StandardCharsets.UTF_8);
        ConfigIO io = new ConfigIO(blocked);

        assertFalse(io.write(sample("first")));
        assertEquals("not a directory", Files.readString(blocked, StandardCharsets.UTF_8));
    }

    @Test
    void unicodeSurvivesARoundTrip() {
        ConfigIO io = new ConfigIO(tempDir);
        JsonObject root = sample("café → 日本語 🎉");

        assertTrue(io.write(root));
        assertEquals("café → 日本語 🎉", io.read().root().get("marker").getAsString());
    }

    private static JsonObject readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
