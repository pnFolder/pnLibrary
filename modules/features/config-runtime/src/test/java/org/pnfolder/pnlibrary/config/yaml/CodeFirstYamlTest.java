package org.pnfolder.pnlibrary.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.config.ConfigCodec;
import org.pnfolder.pnlibrary.api.config.ConfigValidationException;
import org.pnfolder.pnlibrary.api.config.ConfigValidatorBuilder;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.PlatformPnLogger;

class CodeFirstYamlTest {
    @TempDir
    Path directory;

    private static final class Value {
        final int port;

        Value(int port) {
            this.port = port;
        }
    }

    @Test
    void addsNestedDefaultsWithCommentsAndPreservesAdministratorValues() {
        String current = "# Existing comment\n"
                + "database:\n"
                + "  host: custom.example\n"
                + "addon:\n"
                + "  own-key: true\n";
        String defaults = "# Database settings\n"
                + "database:\n"
                + "  # Server port\n"
                + "  port: 3306\n"
                + "  host: localhost\n"
                + "# New feature\n"
                + "feature:\n"
                + "  enabled: true\n";

        YamlDefaultsMerger.Result result = YamlDefaultsMerger.merge(current, defaults);
        assertTrue(result.isChanged());
        assertTrue(result.getAddedPaths().contains("database.port"));
        assertTrue(result.getAddedPaths().contains("feature"));
        assertTrue(result.getContent().contains("host: custom.example"));
        assertTrue(result.getContent().contains("own-key: true"));
        assertTrue(result.getContent().contains("# Server port\n  port: 3306"));
    }

    @Test
    void secondSynchronizationIsIdempotent() {
        String current = "root:\n  existing: 5\n";
        String defaults = "root:\n  existing: 1\n  added: 2\n";
        YamlDefaultsMerger.Result first = YamlDefaultsMerger.merge(current, defaults);
        YamlDefaultsMerger.Result second = YamlDefaultsMerger.merge(first.getContent(), defaults);
        assertFalse(second.isChanged());
        assertEquals(first.getContent(), second.getContent());
    }

    @Test
    void aSectionIndentedWithFourSpacesIsMatchedInsteadOfDuplicated() {
        String current = "root:\n    existing: 5\n";
        String defaults = "root:\n  existing: 1\n  added: 2\n";

        YamlDefaultsMerger.Result result = YamlDefaultsMerger.merge(current, defaults);

        assertEquals(Collections.singletonList("root.added"), result.getAddedPaths());
        assertEquals("root:\n    existing: 5\n\n    added: 2\n", result.getContent());
        assertEquals(Arrays.asList("root", "root.existing", "root.added"), YamlDefaultsMerger.paths(result.getContent()));
    }

    @Test
    void managedLifecycleKeepsLastValueAfterFailedReload() throws IOException {
        File file = directory.resolve("config.yml").toFile();
        ConfigCodec<Value> codec = new ConfigCodec<Value>() {
            @Override
            public String encode(Value value) {
                return "port: " + value.port + "\n";
            }

            @Override
            public Value decode(@NotNull String yaml) {
                Matcher matcher = Pattern.compile("port: (\\d+)").matcher(yaml);
                if (!matcher.find()) {
                    throw new IllegalStateException("port is missing");
                }
                return new Value(Integer.parseInt(matcher.group(1)));
            }
        };
        CodeFirstYaml<Value> managed = new CodeFirstYaml<Value>(file, new Value(3306), codec,
                new PlatformPnLogger(new PlatformLogging() { }, "test", "test"),
                new ConfigValidatorBuilder<Value>().require("port", "invalid", value -> value.port >= 1 && value.port <= 65535).build());

        assertEquals(3306, managed.loadValue().port);
        managed.update(value -> new Value(5432));
        assertEquals(5432, managed.getValue().port);
        Files.write(file.toPath(), "port: 99999\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(ConfigValidationException.class, managed::reload);
        assertEquals(5432, managed.getValue().port);
        managed.unload();
        assertFalse(managed.isLoaded());
    }
}
