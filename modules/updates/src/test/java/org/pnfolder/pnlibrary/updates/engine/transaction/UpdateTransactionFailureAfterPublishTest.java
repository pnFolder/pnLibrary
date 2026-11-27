package org.pnfolder.pnlibrary.updates.engine.transaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactSpecification;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;
import org.pnfolder.pnlibrary.updates.history.configuration.ConfigurationSnapshots;

class UpdateTransactionFailureAfterPublishTest {
    @TempDir
    Path directory;

    @Test
    @DisplayName("failure after the first jar was replaced restores it and keeps the configuration backup")
    void failureAfterTheFirstJarWasReplacedRestoresItAndKeepsTheConfigurationBackup() throws Exception {
        Path plugins = Files.createDirectories(directory.resolve("plugins"));
        Path data = Files.createDirectories(plugins.resolve("pnLibrary"));
        Path first = plugins.resolve("first.jar");
        Files.write(first, new byte[] {1});
        // A non-empty folder where the temporary copy goes makes publishing the second jar fail after the first is in place.
        Path second = plugins.resolve("second.jar");
        Files.createDirectories(plugins.resolve("second.jar.publishing/blocker"));
        Files.write(data.resolve("config.yml"), "version: 1".getBytes(StandardCharsets.UTF_8));
        UUID plan = UUID.randomUUID();
        UUID snapshot = new ConfigurationSnapshots(data.resolve("updates/configurations"), plugins).capture(plan,
            Collections.singletonMap(ProductId.of("first"), new UpdateConfigurationBackup(data, Arrays.asList("config.yml"))));
        Path firstSource = release("first-new.jar", new byte[] {2, 2});
        Path secondSource = release("second-new.jar", new byte[] {3, 3});
        UpdateTransaction transactions = new UpdateTransaction(data.resolve("updates/transactions"), new ArtifactVerifier(1024), plugins);

        assertThrows(Exception.class, () -> transactions.prepareForRestart(Arrays.asList(
            new TransactionArtifact(spec("first", firstSource), firstSource, first),
            new TransactionArtifact(spec("second", secondSource), secondSource, second)), plan, null, snapshot));

        assertArrayEquals(new byte[] {1}, Files.readAllBytes(first));
        assertFalse(Files.exists(second));
        assertEquals("version: 1", new String(Files.readAllBytes(data.resolve("config.yml")), StandardCharsets.UTF_8));
        assertTrue(Files.isDirectory(data.resolve("updates/configurations")));
        assertTrue(transactions.awaitingHealth().isEmpty());
    }

    private Path release(String name, byte[] bytes) throws Exception {
        Path path = directory.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private ArtifactSpecification spec(String id, Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        StringBuilder digest = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            digest.append(String.format(Locale.ROOT, "%02x", value));
        }
        return new ArtifactSpecification(ProductId.of(id), SemanticVersion.parse("2.0.0"), new ApiVersionRange(8, 8),
            path.getFileName().toString(), (long) bytes.length, digest.toString());
    }
}
