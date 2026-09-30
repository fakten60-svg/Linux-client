package wtf.woke.lite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the contract between the code and the packaged mod metadata.
 *
 * <p>These assertions catch the failure modes that only show up as a silent
 * "mod did not load" in game: a drifted mod id, an entrypoint pointing at a
 * class that does not exist, or missing dependency declarations. They read the
 * processed {@code fabric.mod.json} from the classpath, so they also prove that
 * resource processing and version expansion ran.</p>
 */
class WokeLiteMetadataTest {

    private static JsonObject modMetadata() {
        InputStream stream = WokeLiteMetadataTest.class.getResourceAsStream("/fabric.mod.json");
        assertNotNull(stream, "fabric.mod.json must be on the classpath (processResources)");
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception cause) {
            throw new AssertionError("fabric.mod.json must be valid JSON", cause);
        }
    }

    @Test
    void modIdMatchesTheCodeConstant() {
        assertEquals(WokeLite.MOD_ID, modMetadata().get("id").getAsString());
    }

    @Test
    void modIdIsLowercaseAndLoaderSafe() {
        assertTrue(WokeLite.MOD_ID.matches("[a-z0-9_-]+"),
                "Fabric requires [a-z0-9_-] ids, got: " + WokeLite.MOD_ID);
    }

    @Test
    void versionPlaceholderIsExpanded() {
        String version = modMetadata().get("version").getAsString();
        assertEquals(false, version.contains("${version}"), "unexpanded version placeholder: " + version);
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), "unexpected version format: " + version);
    }

    @Test
    void modIsClientOnly() {
        assertEquals("client", modMetadata().get("environment").getAsString());
    }

    @Test
    void everyDeclaredEntrypointExists() {
        JsonObject entrypoints = modMetadata().getAsJsonObject("entrypoints");
        assertNotNull(entrypoints, "entrypoints block is required");

        List<String> classNames = new ArrayList<>();
        entrypoints.getAsJsonArray("client").forEach(element -> classNames.add(element.getAsString()));
        assertEquals(List.of("wtf.woke.lite.WokeLiteClient"), classNames);

        for (String className : classNames) {
            try {
                Class.forName(className);
            } catch (ClassNotFoundException cause) {
                throw new AssertionError("entrypoint class is missing: " + className, cause);
            }
        }
    }

    @Test
    void requiredDependenciesAreDeclared() {
        JsonObject depends = modMetadata().getAsJsonObject("depends");
        for (String key : List.of("fabricloader", "fabric-api", "minecraft", "java")) {
            assertTrue(depends.has(key), "missing depends entry: " + key);
        }
        assertEquals(">=21", depends.get("java").getAsString());
    }

    @Test
    void modMenuIsSuggestedRatherThanRequired() {
        JsonObject metadata = modMetadata();
        assertEquals(false, metadata.getAsJsonObject("depends").has("modmenu"),
                "a hard Mod Menu dependency would stop the mod from loading without it");

        JsonObject suggests = metadata.getAsJsonObject("suggests");
        assertNotNull(suggests, "Mod Menu has to be declared as a suggestion");
        assertEquals(">=17.0.0", suggests.get("modmenu").getAsString());

        // The integration class is deliberately not loaded here: Mod Menu is not on
        // the test classpath, which is exactly what "optional" means.
        assertEquals("wtf.woke.lite.ModMenuIntegration",
                metadata.getAsJsonObject("entrypoints").getAsJsonArray("modmenu").get(0).getAsString());
    }

    @Test
    void mixinConfigsAreDeclaredAndPresent() {
        JsonObject metadata = modMetadata();
        assertEquals(2, metadata.getAsJsonArray("mixins").size(),
                "one common and one client mixin config, both listed in the metadata");

        for (String config : new String[] {"wokewtf-lite.mixins.json", "wokewtf-lite.client.mixins.json"}) {
            assertNotNull(WokeLiteMetadataTest.class.getResourceAsStream("/" + config),
                    "missing mixin config: " + config);
        }
    }

    @Test
    void everyShippedMixinIsListedInItsConfig() {
        // A mixin class that is not named in a config is loaded as an ordinary
        // class: no error, no injection, and the feature it carries is simply
        // absent. Naming them here is what makes forgetting one visible.
        JsonObject clientConfig = readJson("/wokewtf-lite.client.mixins.json");
        List<String> listed = new ArrayList<>();
        clientConfig.getAsJsonArray("client").forEach(element -> listed.add(element.getAsString()));

        assertEquals(List.of("BookEditScreenMixin", "ChatHudMixin", "ScreenshotShareMixin"), listed);
        assertEquals("wtf.woke.lite.mixin.client", clientConfig.get("package").getAsString());
    }

    @Test
    void theCommonMixinConfigStaysEmpty() {
        JsonObject commonConfig = readJson("/wokewtf-lite.mixins.json");

        assertEquals(0, commonConfig.getAsJsonArray("mixins").size(),
                "this mod patches client behaviour only, so the common config must stay empty");
    }

    private static JsonObject readJson(String resource) {
        InputStream stream = WokeLiteMetadataTest.class.getResourceAsStream(resource);
        assertNotNull(stream, "missing resource: " + resource);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception cause) {
            throw new AssertionError(resource + " must be valid JSON", cause);
        }
    }
}
