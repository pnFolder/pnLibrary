package org.pnfolder.pnlibrary.updates.engine.transaction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactDownloadException;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactDownloader;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactSpecification;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerificationException;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;

class UpdateTransactionTest {
    private static final long LIMIT = 1024 * 1024;
    private static final String WRONG_DIGEST = repeat("00", 32);

    @TempDir
    Path directory;

    @Test
    @DisplayName("corrupt journal is skipped with a warning and does not break other operations")
    void corruptJournalIsSkippedWithAWarningAndDoesNotBreakOtherOperations() throws Exception {
        Path root = directory.resolve("transactions");
        Path directoryOfBroken = root.resolve(UUID.randomUUID().toString());
        Files.createDirectories(directoryOfBroken);
        Files.write(directoryOfBroken.resolve("journal.json"), new byte[0]);
        List<String> warnings = new ArrayList<>();
        UpdateTransaction transaction = new UpdateTransaction(root, new ArtifactVerifier(1024),
            root.toAbsolutePath().normalize().getParent(), warnings::add);

        assertEquals(Collections.emptyList(), transaction.recoverAll());
        assertEquals(Collections.emptyList(), transaction.awaitingHealth());
        assertEquals(Collections.emptyList(), transaction.awaitingRollbackHealth());
        assertEquals(Collections.emptyList(), transaction.pendingInstallations());
        assertEquals(Collections.emptyList(), transaction.failedHealthChecks());
        assertTrue(transaction.automaticUpdatesHealthy(Collections.singleton("market")));
        boolean allMentionJournal = true;
        for (String warning : warnings) {
            allMentionJournal &= warning.contains("journal.json");
        }
        assertTrue(!warnings.isEmpty() && allMentionJournal, warnings.toString());
    }

    @Test
    @DisplayName("verifier rejects checksum mismatches")
    void verifierRejectsChecksumMismatches() throws Exception {
        Path jar = artifact("market.jar", new byte[] {1, 2, 3});
        ArtifactSpecification valid = specification(jar, "market", "2.0.0", 4);
        new ArtifactVerifier(1024 * 1024).verify(jar, valid);

        assertThrows(ArtifactVerificationException.class,
            () -> new ArtifactVerifier(1024 * 1024).verify(jar, valid.withSha256(WRONG_DIGEST)));
    }

    @Test
    @DisplayName("downloader bounds bytes and never exposes a partial destination")
    void downloaderBoundsBytesAndNeverExposesAPartialDestination() throws Exception {
        Path destination = directory.resolve("download.jar");
        assertThrows(ArtifactDownloadException.class,
            () -> new ArtifactDownloader(3).download(() -> new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), destination));
        assertEquals(false, Files.exists(destination));

        assertEquals(3L, new ArtifactDownloader(3).download(() -> new ByteArrayInputStream(new byte[] {1, 2, 3}), destination));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(destination));
    }

    @Test
    @DisplayName("commits an atomic component set after health check")
    void commitsAnAtomicComponentSetAfterHealthCheck() throws Exception {
        Path targets = Files.createDirectories(directory.resolve("plugins"));
        Path marketTarget = write(targets.resolve("market.jar"), new byte[] {9});
        Path authTarget = write(targets.resolve("auth.jar"), new byte[] {8});
        Path market = artifact("market-new.jar", new byte[] {1});
        Path auth = artifact("auth-new.jar", new byte[] {2});
        UpdateTransaction transaction = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT));

        TransactionResult result = transaction.apply(Arrays.asList(
            new TransactionArtifact(specification(market, "market", "2.0.0", 4), market, marketTarget),
            new TransactionArtifact(specification(auth, "auth", "3.0.0", 4), auth, authTarget)), () -> true);

        assertEquals(TransactionState.COMMITTED, result.getState());
        assertArrayEquals(Files.readAllBytes(market), Files.readAllBytes(marketTarget));
        assertArrayEquals(Files.readAllBytes(auth), Files.readAllBytes(authTarget));
        assertEquals(TransactionState.COMMITTED, TransactionJournal.load(result.getJournal()).getState());
    }

    @Test
    @DisplayName("commits three verified components as one transaction")
    void commitsThreeVerifiedComponentsAsOneTransaction() throws Exception {
        Path targets = Files.createDirectories(directory.resolve("plugins"));
        List<TransactionArtifact> artifacts = new ArrayList<>();
        String[] ids = {"library", "market", "auth"};
        for (int index = 0; index < ids.length; index++) {
            Path source = artifact(ids[index] + "-new.jar", new byte[] {(byte) index});
            artifacts.add(new TransactionArtifact(specification(source, ids[index], "2.0.0", 2), source,
                targets.resolve(ids[index] + ".jar")));
        }
        TransactionResult result = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT))
            .apply(artifacts, () -> true);
        assertEquals(TransactionState.COMMITTED, result.getState());
        for (TransactionArtifact artifact : artifacts) {
            assertArrayEquals(Files.readAllBytes(artifact.getSource()), Files.readAllBytes(artifact.getTarget()));
        }
    }

    @Test
    @DisplayName("rejects publication target outside allowed root")
    void rejectsPublicationTargetOutsideAllowedRoot() throws Exception {
        Path source = artifact("market-new.jar", new byte[] {1});
        UpdateTransaction transaction = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT));
        assertThrows(IllegalArgumentException.class, () -> transaction.apply(Collections.singletonList(
            new TransactionArtifact(specification(source, "market", "2.0.0", 1), source,
                directory.getParent().resolve("escaped-market.jar"))), () -> true));
    }

    @Test
    @DisplayName("failed health check waits for administrator before rollback")
    void failedHealthCheckWaitsForAdministratorBeforeRollback() throws Exception {
        Path targets = Files.createDirectories(directory.resolve("plugins"));
        Path marketTarget = write(targets.resolve("market.jar"), new byte[] {9});
        Path authTarget = write(targets.resolve("auth.jar"), new byte[] {8});
        Path market = artifact("market-new.jar", new byte[] {1});
        Path auth = artifact("auth-new.jar", new byte[] {2});

        TransactionResult result = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT)).apply(
            Arrays.asList(
                new TransactionArtifact(specification(market, "market", "2.0.0", 4), market, marketTarget),
                new TransactionArtifact(specification(auth, "auth", "3.0.0", 4), auth, authTarget)), () -> false);

        assertEquals(TransactionState.FAILED, result.getState());
        assertArrayEquals(Files.readAllBytes(market), Files.readAllBytes(marketTarget));
        assertArrayEquals(Files.readAllBytes(auth), Files.readAllBytes(authTarget));

        TransactionResult rolledBack = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT))
            .rollback(result.getJournal());
        assertEquals(TransactionState.ROLLED_BACK, rolledBack.getState());
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(marketTarget));
        assertArrayEquals(new byte[] {8}, Files.readAllBytes(authTarget));
    }

    @Test
    @DisplayName("rollback restores installed jar into platform update directory")
    void rollbackRestoresInstalledJarIntoPlatformUpdateDirectory() throws Exception {
        Path installed = directory.resolve("plugins/market.jar");
        Files.createDirectories(installed.getParent());
        Files.write(installed, new byte[] {9, 8, 7});
        Path updateTarget = directory.resolve("plugins/update/market.jar");
        Path replacement = artifact("market-new.jar", new byte[] {1});
        UpdateTransaction transaction = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT));

        TransactionResult applied = transaction.apply(Collections.singletonList(new TransactionArtifact(
            specification(replacement, "market", "2.0.0", 4), replacement, updateTarget, installed)), () -> true);
        Files.delete(updateTarget);

        TransactionResult rolledBack = transaction.rollback(applied.getJournal());

        assertEquals(TransactionState.ROLLED_BACK, rolledBack.getState());
        assertArrayEquals(new byte[] {9, 8, 7}, Files.readAllBytes(updateTarget));
    }

    @Test
    @DisplayName("prepared update remains pending across restart until health is confirmed")
    void preparedUpdateRemainsPendingAcrossRestartUntilHealthIsConfirmed() throws Exception {
        Path transactionRoot = directory.resolve("transactions");
        Path transactionDirectory = transactionRoot.resolve("interrupted");
        Path backupDirectory = Files.createDirectories(transactionDirectory.resolve("backup"));
        Path target = write(directory.resolve("market.jar"), new byte[] {2});
        Path backup = write(backupDirectory.resolve("market.jar"), new byte[] {1});
        Path staged = write(transactionDirectory.resolve("staged.jar"), new byte[] {2});
        Path journalPath = transactionDirectory.resolve("journal.json");
        TransactionJournal.save(journalPath, new TransactionJournal("interrupted", TransactionState.AWAITING_HEALTH,
            Collections.singletonList(new JournalArtifact("market", target.toString(), staged.toString(),
                backup.toString(), true, "2.0.0"))));

        UpdateTransaction transaction = new UpdateTransaction(transactionRoot, new ArtifactVerifier(1024));
        TransactionResult result = transaction.recover(journalPath);

        assertEquals(TransactionState.AWAITING_HEALTH, result.getState());
        assertArrayEquals(new byte[] {2}, Files.readAllBytes(target));
        assertEquals(Collections.singletonMap("market", "2.0.0"), transaction.awaitingHealth().get(0).getExpectedVersions());
        assertEquals(1, transaction.awaitingHealth().size());
        assertEquals(TransactionState.AWAITING_HEALTH, transaction.recoverAll().get(0).getState());
        assertEquals(1, transaction.recoverAll().size());
        assertEquals(TransactionState.COMMITTED, transaction.completeHealth(journalPath, true).getState());
    }

    @Test
    @DisplayName("rollback candidate skips a newer failed transaction without backups")
    void rollbackCandidateSkipsANewerFailedTransactionWithoutBackups() throws Exception {
        Path target = directory.resolve("plugins/market.jar");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[] {9});
        Path valid = artifact("valid.jar", new byte[] {1});
        UpdateTransaction transaction = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT));
        TransactionResult committed = transaction.apply(Collections.singletonList(
            new TransactionArtifact(specification(valid, "market", "2.0.0", 4), valid, target)), () -> true);

        Path invalid = artifact("invalid.jar", new byte[] {2});
        assertThrows(ArtifactVerificationException.class, () -> transaction.apply(Collections.singletonList(
            new TransactionArtifact(specification(invalid, "market", "3.0.0", 4).withSha256(WRONG_DIGEST), invalid, target)),
            () -> true));

        assertEquals(committed.getJournal(), transaction.latestRollbackCandidate());
    }

    @Test
    @DisplayName("set that failed before publication is never offered for rollback")
    void setThatFailedBeforePublicationIsNeverOfferedForRollback() throws Exception {
        Path plugins = Files.createDirectories(directory.resolve("plugins"));
        Path newTarget = plugins.resolve("market.jar");
        UpdateTransaction transaction = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT), plugins);
        Path invalid = artifact("market-new.jar", new byte[] {1});
        assertThrows(ArtifactVerificationException.class, () -> transaction.prepareForRestart(Collections.singletonList(
            new TransactionArtifact(specification(invalid, "market", "2.0.0", 4).withSha256(WRONG_DIGEST), invalid, newTarget))));
        Path only = directory.resolve("transactions").toFile().listFiles()[0].toPath();
        TransactionJournal failed = transaction.readJournal(only.resolve("journal.json"));
        assertEquals(TransactionState.FAILED, failed.getState());
        Files.write(newTarget, new byte[] {5});

        assertTrue(transaction.rollbackCandidates().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> transaction.rollbackCandidate(UUID.fromString(failed.getId())));
        assertArrayEquals(new byte[] {5}, Files.readAllBytes(newTarget));
    }

    @Test
    @DisplayName("rollback of a new component removes it and a leftover publishing copy")
    void rollbackOfANewComponentRemovesItAndALeftoverPublishingCopy() throws Exception {
        Path plugins = Files.createDirectories(directory.resolve("plugins"));
        Path target = plugins.resolve("market.jar");
        UpdateTransaction transaction = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT), plugins);
        Path source = artifact("market-new.jar", new byte[] {1});
        TransactionResult applied = transaction.apply(Collections.singletonList(
            new TransactionArtifact(specification(source, "market", "2.0.0", 4), source, target)), () -> true);
        Path leftover = write(plugins.resolve("market.jar.publishing"), new byte[] {3});

        transaction.rollback(applied.getJournal());

        assertEquals(false, Files.exists(target));
        assertEquals(false, Files.exists(leftover));
    }

    @Test
    @DisplayName("rollback replaces the target through a sibling copy")
    void rollbackReplacesTheTargetThroughASiblingCopy() throws Exception {
        Path plugins = Files.createDirectories(directory.resolve("plugins"));
        Path target = write(plugins.resolve("market.jar"), new byte[] {9});
        UpdateTransaction transaction = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT), plugins);
        Path source = artifact("market-new.jar", new byte[] {1});
        TransactionResult applied = transaction.apply(Collections.singletonList(
            new TransactionArtifact(specification(source, "market", "2.0.0", 4), source, target)), () -> true);
        Files.write(plugins.resolve("market.jar.publishing"), new byte[] {3});

        transaction.rollback(applied.getJournal());

        assertArrayEquals(new byte[] {9}, Files.readAllBytes(target));
        assertEquals(false, Files.exists(plugins.resolve("market.jar.publishing")));
    }

    @Test
    @DisplayName("selected rollback restores that saved set rather than the latest one")
    void selectedRollbackRestoresThatSavedSetRatherThanTheLatestOne() throws Exception {
        Path plugins = Files.createDirectories(directory.resolve("plugins"));
        Path target = write(plugins.resolve("market.jar"), new byte[] {9});
        UpdateTransaction transaction = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT), plugins);
        Path first = artifact("first.jar", new byte[] {1});
        TransactionResult saved = transaction.apply(Collections.singletonList(
            new TransactionArtifact(specification(first, "market", "2.0.0", 4), first, target)), () -> true);
        Path second = artifact("second.jar", new byte[] {2});
        transaction.apply(Collections.singletonList(
            new TransactionArtifact(specification(second, "market", "3.0.0", 4), second, target)), () -> true);
        assertEquals(2, transaction.rollbackCandidates().size());
        UUID id = UUID.fromString(TransactionJournal.load(saved.getJournal()).getId());
        assertEquals(saved.getJournal(), transaction.rollbackCandidate(id));
        transaction.rollback(transaction.rollbackCandidate(id));
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(target));
        assertThrows(Exception.class, () -> transaction.rollbackCandidate(UUID.randomUUID()));
    }

    @Test
    @DisplayName("an unreadable journal is reported and does not hide the other rollback sets")
    void anUnreadableJournalIsReportedAndDoesNotHideTheOtherRollbackSets() throws Exception {
        Path plugins = Files.createDirectories(directory.resolve("plugins"));
        Path target = write(plugins.resolve("market.jar"), new byte[] {9});
        Path root = directory.resolve("transactions");
        List<String> warnings = new ArrayList<>();
        UpdateTransaction transaction = new UpdateTransaction(root, new ArtifactVerifier(LIMIT), plugins, warnings::add);
        Path source = artifact("first.jar", new byte[] {1});
        TransactionResult saved = transaction.apply(Collections.singletonList(
            new TransactionArtifact(specification(source, "market", "2.0.0", 4), source, target)), () -> true);
        Path broken = Files.createDirectories(root.resolve(UUID.randomUUID().toString()));
        Files.write(broken.resolve("journal.json"), new byte[0]);

        assertEquals(Collections.singletonList(saved.getJournal()), transaction.rollbackCandidates());
        assertEquals(saved.getJournal(), transaction.latestRollbackCandidate());
        boolean mentioned = false;
        for (String warning : warnings) {
            mentioned |= warning.contains("journal.json");
        }
        assertTrue(mentioned, warnings.toString());
    }

    @Test
    @DisplayName("tampered target is rejected before any rollback file is changed")
    void tamperedTargetIsRejectedBeforeAnyRollbackFileIsChanged() throws Exception {
        Path plugins = Files.createDirectories(directory.resolve("plugins"));
        List<Path> targets = new ArrayList<>();
        for (String name : new String[] {"market", "auth"}) {
            targets.add(write(plugins.resolve(name + ".jar"), new byte[] {9}));
        }
        UpdateTransaction transaction = new UpdateTransaction(directory.resolve("transactions"), new ArtifactVerifier(LIMIT), plugins);
        List<TransactionArtifact> inputs = new ArrayList<>();
        String[] names = {"market", "auth"};
        for (int index = 0; index < names.length; index++) {
            Path source = artifact(names[index] + "-new.jar", new byte[] {(byte) index});
            inputs.add(new TransactionArtifact(specification(source, names[index], "2.0.0", 4), source, targets.get(index)));
        }
        TransactionResult saved = transaction.apply(inputs, () -> true);
        Path outside = write(directory.resolve("outside.jar"), new byte[] {7});
        TransactionJournal journal = TransactionJournal.load(saved.getJournal());
        TransactionJournal.save(saved.getJournal(), journal.withArtifacts(Arrays.asList(journal.getArtifacts().get(0),
            journal.getArtifacts().get(1).withTarget(outside.toString()))));
        assertThrows(IllegalArgumentException.class, () -> transaction.rollback(saved.getJournal()));
        assertArrayEquals(Files.readAllBytes(inputs.get(0).getSource()), Files.readAllBytes(targets.get(0)));
        assertArrayEquals(new byte[] {7}, Files.readAllBytes(outside));
        assertTrue(transaction.rollbackCandidates().isEmpty());
    }

    private ArtifactSpecification specification(Path path, String id, String version, int api) throws Exception {
        return new ArtifactSpecification(ProductId.of(id), SemanticVersion.parse(version), new ApiVersionRange(api, api),
            path.getFileName().toString(), Files.size(path), sha256(path));
    }

    private Path artifact(String name, byte[] payload) throws IOException {
        Path path = directory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("payload.bin"));
            output.write(payload);
            output.closeEntry();
        }
        return path;
    }

    private static Path write(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        return path;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", value));
        }
        return hex.toString();
    }

    private static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
