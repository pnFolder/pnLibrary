package org.pnfolder.pnlibrary.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.ConfigSteps;

class YamlMigrationEngineTest {
    @TempDir
    Path directory;

    private final YamlMigrationEngine engine = new YamlMigrationEngine();

    private static final class Steps extends ConfigSteps<Object> {
        Steps() {
            version(2, changes -> changes.rename("old", "new"));
            version(3, changes -> {
                changes.put("db.host", changes.get("host"));
                changes.remove("host");
            });
            version(4, changes -> changes.remove("legacy"));
        }
    }

    private static final class Failing extends ConfigSteps<Object> {
        Failing() {
            version(2, changes -> changes.put("n", changes.get("count").toInt()));
        }
    }

    @Test
    void renamesAKeyAndMovesAValueIntoANestedSection() {
        YamlMigrationEngine.Result result = engine.migrate("old: 1\nhost: h\nlegacy: x\nkeep: k\n", new Steps());

        assertEquals(Arrays.asList("v2", "v3", "v4"), result.getApplied());
        assertTrue(result.getContent().contains("new: 1"), result.getContent());
        assertTrue(result.getContent().contains("db:\n  host: h"), result.getContent());
        assertTrue(result.getContent().contains("keep: k"));
        assertFalse(result.getContent().contains("old:"));
        assertFalse(result.getContent().contains("legacy"));
    }

    @Test
    void theResultCarriesTheLatestVersionMarkerOnce() {
        YamlMigrationEngine.Result result = engine.migrate("old: 1\nhost: h\n", new Steps());

        Matcher markers = Pattern.compile("(?m)^_config-version: ").matcher(result.getContent());
        int count = 0;
        while (markers.find()) {
            count++;
        }
        assertEquals(1, count, result.getContent());
        assertTrue(result.getContent().contains("_config-version: 4\n"));
    }

    @Test
    void onlyVersionsAboveTheStoredOneAreApplied() {
        YamlMigrationEngine.Result result = engine.migrate("_config-version: 3\nlegacy: x\nold: stays\n", new Steps());

        assertEquals(Collections.singletonList("v4"), result.getApplied());
        assertTrue(result.getContent().contains("old: stays"), result.getContent());
        assertFalse(result.getContent().contains("legacy"));
    }

    @Test
    void aFileAtTheLatestVersionIsNotChangedInMeaning() {
        YamlMigrationEngine.Result result = engine.migrate("_config-version: 4\nnew: 1\n", new Steps());

        assertTrue(result.getApplied().isEmpty());
        assertFalse(result.isNewerThanCode());
        assertTrue(result.getContent().contains("new: 1"));
    }

    @Test
    void aNewerFileIsReturnedAsIsAndFlagged() {
        String text = "_config-version: 9\nold: 1\n";

        YamlMigrationEngine.Result result = engine.migrate(text, new Steps());

        assertTrue(result.isNewerThanCode());
        assertEquals(text, result.getContent());
        assertTrue(result.getApplied().isEmpty());
    }

    @Test
    void anEmptyFileGetsOnlyTheMarker() {
        YamlMigrationEngine.Result result = engine.migrate("", ConfigSteps.NONE);

        assertTrue(result.getContent().endsWith("_config-version: " + ConfigSteps.NONE.getLatestVersion() + "\n"),
                result.getContent());
        assertTrue(result.getApplied().isEmpty());
    }

    @Test
    void aRootThatIsNotAnObjectIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> engine.migrate("- a\n- b\n", new Steps()));
    }

    @Test
    void aFailingStepThrowsAndTheFileOnDiskStaysAsItWas() throws IOException {
        Path file = directory.resolve("c.yml");
        String text = "count: not-a-number\n";
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class,
                () -> engine.migrate(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), new Failing()));
        assertEquals(text, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }
}
