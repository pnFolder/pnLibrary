package org.pnfolder.pnlibrary.updates.install;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
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
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerificationException;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionResult;
import org.pnfolder.pnlibrary.updates.engine.transaction.TransactionState;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction;
import org.pnfolder.pnlibrary.updates.history.ConfigurationSnapshots;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;
import org.pnfolder.pnlibrary.updates.service.UpdateInstallationLock;

class OfflineUpdateInstallerTest {
    @TempDir
    Path directory;

    @Test
    @DisplayName("offline application installs and rolls back without repeating completed work")
    void offlineApplicationInstallsAndRollsBackWithoutRepeatingCompletedWork() throws Exception {
        Fixture fixture = prepare(false);
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(fixture.active));
        assertEquals(1, OfflineUpdateInstaller.apply(fixture.data));
        assertArrayEquals(new byte[] {2, 3}, Files.readAllBytes(fixture.active));
        assertFalse(Files.exists(fixture.pending));
        assertEquals(0, OfflineUpdateInstaller.apply(fixture.data));
        assertEquals(1, fixture.transactions.awaitingHealth().size());
        fixture.transactions.completeHealth(fixture.journal, true);
        assertEquals(1, fixture.transactions.rollbackCandidates().size());
        fixture.transactions.rollback(fixture.journal);
        assertEquals(1, OfflineUpdateInstaller.apply(fixture.data));
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(fixture.active));
        assertEquals(0, OfflineUpdateInstaller.apply(fixture.data));
    }

    @Test
    @DisplayName("active runtime lock prevents any offline changes")
    void activeRuntimeLockPreventsAnyOfflineChanges() throws Exception {
        Fixture fixture = prepare(false);
        try (UpdateInstallationLock lock = UpdateInstallationLock.acquire(new LibraryPaths(fixture.data))) {
            assertThrows(IllegalStateException.class, () -> OfflineUpdateInstaller.apply(fixture.data));
        }
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(fixture.active));
        assertEquals(1, OfflineUpdateInstaller.apply(fixture.data));
    }

    @Test
    @DisplayName("corrupt preserved payload is rejected before replacing an installed jar")
    void corruptPreservedPayloadIsRejectedBeforeReplacingAnInstalledJar() throws Exception {
        Fixture fixture = prepare(false);
        TransactionJournal journal = TransactionJournal.load(fixture.journal);
        Files.write(Paths.get(journal.getArtifacts().get(0).getStaged()), new byte[] {7});
        assertThrows(ArtifactVerificationException.class, () -> OfflineUpdateInstaller.apply(fixture.data));
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(fixture.active));
    }

    @Test
    @DisplayName("runtime recovery refuses to rewrite installed jars after interrupted offline publication")
    void runtimeRecoveryRefusesToRewriteInstalledJarsAfterInterruptedOfflinePublication() throws Exception {
        Fixture fixture = prepare(false);
        Path source = directory.resolve("interrupted.jar");
        Files.write(source, new byte[] {8});
        ArtifactSpecification specification = new ArtifactSpecification(ProductId.of("pnlibrary"),
            SemanticVersion.parse("3.0.0"), new ApiVersionRange(8, 8), source.getFileName().toString());
        TransactionResult result = fixture.transactions.prepareForRestart(
            Collections.singletonList(new TransactionArtifact(specification, source, fixture.active)), null, null, null,
            "INSTALL", UUID.fromString(TransactionJournal.load(fixture.journal).getId()));
        TransactionJournal journal = TransactionJournal.load(result.getJournal());
        journal.setState(TransactionState.PUBLISHING);
        TransactionJournal.save(result.getJournal(), journal);
        assertThrows(IllegalArgumentException.class, () -> fixture.transactions.recoverAll());
        assertArrayEquals(new byte[] {8}, Files.readAllBytes(fixture.active));
        fixture.transactions.recoverAllOffline();
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(fixture.active));
    }

    @Test
    @DisplayName("failure after configuration replacement restores both previous jars and previous settings")
    void failureAfterConfigurationReplacementRestoresBothPreviousJarsAndPreviousSettings() throws Exception {
        Fixture fixture = prepare(true);
        OfflineUpdateInstaller.apply(fixture.data);
        Path config = fixture.data.resolve("config.yml");
        Files.write(config, "version: 2".getBytes(StandardCharsets.UTF_8));
        fixture.transactions.completeHealth(fixture.journal, true);
        fixture.transactions.rollback(fixture.journal, true);
        // A collision in the platform update directory is discovered during final consumption.
        Files.write(fixture.pending, new byte[] {99});
        assertThrows(ArtifactVerificationException.class, () -> OfflineUpdateInstaller.apply(fixture.data));
        assertArrayEquals(new byte[] {2, 3}, Files.readAllBytes(fixture.active));
        assertEquals("version: 2", new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
        assertArrayEquals(new byte[] {99}, Files.readAllBytes(fixture.pending));
    }

    private static final class Fixture {
        private final Path data;
        private final Path active;
        private final Path pending;
        private final Path journal;
        private final UpdateTransaction transactions;

        Fixture(Path data, Path active, Path pending, Path journal, UpdateTransaction transactions) {
            this.data = data;
            this.active = active;
            this.pending = pending;
            this.journal = journal;
            this.transactions = transactions;
        }
    }

    private Fixture prepare(boolean configuration) throws Exception {
        Path plugins = directory.resolve("plugins");
        Path data = plugins.resolve("pnLibrary");
        Files.createDirectories(data);
        Path active = plugins.resolve("library.jar");
        Files.write(active, new byte[] {1});
        Path pending = plugins.resolve("update/library.jar");
        byte[] bytes = {2, 3};
        Path source = directory.resolve("release.jar");
        Files.write(source, bytes);
        ApiVersionRange api = new ApiVersionRange(8, 8);
        StringBuilder digest = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            digest.append(String.format("%02x", value));
        }
        ArtifactSpecification specification = new ArtifactSpecification(ProductId.of("pnlibrary"),
            SemanticVersion.parse("2.0.0"), api, "release.jar", (long) bytes.length, digest.toString());
        UpdateTransaction transactions = new UpdateTransaction(new LibraryPaths(data).getTransactions(),
            new ArtifactVerifier(1024), plugins);
        UUID plan = UUID.randomUUID();
        UUID snapshot = null;
        if (configuration) {
            Files.write(data.resolve("config.yml"), "version: 1".getBytes(StandardCharsets.UTF_8));
            snapshot = new ConfigurationSnapshots(new LibraryPaths(data).getConfigurations(), plugins).capture(plan,
                Collections.singletonMap(ProductId.of("pnlibrary"),
                    new UpdateConfigurationBackup(data, Collections.singletonList("config.yml"))));
        }
        TransactionResult result = transactions.prepareForRestart(Collections.singletonList(
            new TransactionArtifact(specification, source, pending, active, "1.0.0", api, active)), plan, 8, snapshot);
        return new Fixture(data, active, pending, result.getJournal(), transactions);
    }
}
