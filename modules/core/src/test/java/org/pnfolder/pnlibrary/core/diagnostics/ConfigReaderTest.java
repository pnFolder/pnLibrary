package org.pnfolder.pnlibrary.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.core.diagnostics.config.ConfigReader;

class ConfigReaderTest {
    @TempDir
    Path dataFolder;

    @Test
    @SuppressWarnings("unchecked")
    void readsYamlFileAndAppliesExclusionsAndSecretRedaction() throws IOException {
        String yaml = "storage:\n"
            + "  host: \"localhost\"\n"
            + "  password: \"my_db_password\"\n"
            + "  internalPool:\n"
            + "    maxSize: 10\n"
            + "featureFlags:\n"
            + "  beta: true\n"
            + "licenseKey: \"license-ABC123XYZ\"";
        Files.write(dataFolder.resolve("config.yml"), yaml.getBytes(StandardCharsets.UTF_8));

        DiagnosticConfiguration spec = DiagnosticConfiguration.builder("config.yml")
            .exclude("storage.internalPool")
            .secretKeyRegex("(?i).*password.*")
            .redactValueRegex("license-[A-Za-z0-9]+")
            .build();

        Map<String, Object> result = new ConfigReader(dataFolder).readAndRedact(spec);

        assertNotNull(result.get("data"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        Map<String, Object> storage = (Map<String, Object>) data.get("storage");

        assertEquals("localhost", storage.get("host"));
        assertEquals("[REDACTED SECRET KEY]", storage.get("password"));
        assertEquals("[EXCLUDED BY RULE]", storage.get("internalPool"));
        assertEquals("[REDACTED VALUE]", data.get("licenseKey"));
    }

    @Test
    void blocksPathTraversal() {
        DiagnosticConfiguration spec = DiagnosticConfiguration.builder("config.yml").build();

        Map<String, Object> result = new ConfigReader(dataFolder).readAndRedact(spec);

        assertNotNull(result.get("error"));
    }

    @Test
    void blocksEscapeThroughASymlinkedParentDirectory() throws IOException {
        Path outside = Files.createTempDirectory("pnlibrary-outside");
        try {
            Files.write(outside.resolve("config.yml"), "safe: false\n".getBytes(StandardCharsets.UTF_8));
            Path link = dataFolder.resolve("linked");
            boolean linked = createSymlink(link, outside);
            assumeTrue(linked, "symbolic links are unavailable on this test host");

            DiagnosticConfiguration spec = DiagnosticConfiguration.builder("linked/config.yml").build();
            Map<String, Object> result = new ConfigReader(dataFolder).readAndRedact(spec);

            assertEquals("[SECURITY: symlink escape blocked]", result.get("error"));
        } finally {
            Files.deleteIfExists(outside.resolve("config.yml"));
            Files.deleteIfExists(outside);
        }
    }

    private static boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            return false;
        }
    }
}
