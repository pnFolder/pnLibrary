package org.pnfolder.pnlibrary.updates.install;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.updates.release.ArtifactDescriptor;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.release.ProductRelease;
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerificationException;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;
import org.pnfolder.pnlibrary.updates.engine.net.StreamReader;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;
import org.pnfolder.pnlibrary.updates.engine.transaction.journal.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.UpdateTransaction;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

class UpdatePlanStagerTest {
    @TempDir
    Path directory;

    private final byte[] bytes = {1, 2, 3};
    private final ProductId product = ProductId.of("pnlibrary");
    private final SemanticVersion version = SemanticVersion.parse("2.0.0");
    private final AtomicInteger downloads = new AtomicInteger();

    @Test
    @DisplayName("published digest and size are checked before an update is exposed")
    void publishedDigestAndSizeAreCheckedBeforeAnUpdateIsExposed() throws IOException {
        assertThrows(ArtifactVerificationException.class, () -> stage(repeat("00", 32), (long) bytes.length));
        assertFalse(Files.exists(directory.resolve("plugins/update/library.jar")));
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(directory.resolve("plugins/library.jar")));
        assertThrows(ArtifactVerificationException.class, () -> stage(digest(), 100L));
        assertFalse(Files.exists(directory.resolve("plugins/update/library.jar")));
    }

    @Test
    @DisplayName("missing required checksum is rejected before making a download request")
    void missingRequiredChecksumIsRejectedBeforeMakingADownloadRequest() {
        assertThrows(IllegalArgumentException.class, () -> stage(null, null));
        assertEquals(0, downloads.get());
    }

    @Test
    @DisplayName("matching release metadata stages bytes while leaving the running artifact alone")
    void matchingReleaseMetadataStagesBytesWhileLeavingTheRunningArtifactAlone() throws IOException {
        stage(digest(), (long) bytes.length);
        assertArrayEquals(bytes, Files.readAllBytes(directory.resolve("plugins/update/library.jar")));
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(directory.resolve("plugins/library.jar")));
    }

    @Test
    @DisplayName("saved transaction identifies the backup version time and originating plan")
    void savedTransactionIdentifiesTheBackupVersionTimeAndOriginatingPlan() throws IOException {
        Instant before = Instant.now();
        stage(digest(), (long) bytes.length);
        Path root = directory.resolve("plugins/pnLibrary/data/updates/transactions");
        Path path;
        try (Stream<Path> children = Files.list(root)) {
            path = children.findFirst().get().resolve("journal.json");
        }
        TransactionJournal journal = TransactionJournal.load(path);
        assertFalse(Instant.parse(journal.getCreatedAt()).isBefore(before));
        assertNotNull(UUID.fromString(journal.getPlanId()));
        assertEquals("1.0.0", journal.getArtifacts().get(0).getPreviousVersion());
        assertEquals("2.0.0", journal.getArtifacts().get(0).getExpectedVersion());
    }

    @Test
    @DisplayName("refuses to download from a host outside the trusted list")
    void refusesToDownloadFromAHostOutsideTheTrustedList() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> stage(digest(), (long) bytes.length, "https://evil.example.com/library-new.jar"));
        assertTrue(error.getMessage().contains("evil.example.com"));
        assertEquals(0, downloads.get());
    }

    @Test
    @DisplayName("the Java build the administrator chose wins, then the one that matches the installed product")
    void theJavaBuildTheAdministratorChoseWinsThenTheOneThatMatchesTheInstalledProduct() throws IOException {
        assertEquals("build-11.jar", downloadedBuild(11, 8));
        assertEquals("build-8.jar", downloadedBuild(8, 11));
        assertEquals("build-8.jar", downloadedBuild(null, 8));
        assertEquals("build-11.jar", downloadedBuild(null, 11));
        // nothing chosen and nothing known: the old behaviour, the first build that fits
        assertEquals("build-8.jar", downloadedBuild(null, null));
        // a choice no build fits is ignored in favour of the installed one
        assertEquals("build-11.jar", downloadedBuild(99, 11));
    }

    private String downloadedBuild(Integer choice, Integer installed) throws IOException {
        Path root = Files.createTempDirectory(directory, "choice");
        Path plugins = root.resolve("plugins");
        Path data = plugins.resolve("pnLibrary");
        Files.createDirectories(data);
        Path current = plugins.resolve("library.jar");
        Files.write(current, new byte[] {9});
        String[] downloaded = {""};
        TrustedHttpClient http = new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            @Override
            public <T> T stream(URI uri, long maximumBytes, StreamReader<T> reader) throws IOException {
                String path = uri.getPath();
                downloaded[0] = path.substring(path.lastIndexOf('/') + 1);
                return reader.read(new ByteArrayInputStream(bytes));
            }
        };
        List<ArtifactDescriptor> artifacts = Arrays.asList(8, 11).stream().map(level ->
            new ArtifactDescriptor("build-" + level + ".jar", PlatformType.BUKKIT, level, null, (long) bytes.length, digest(),
                URI.create("https://github.com/example/repo/releases/download/v2/build-" + level + ".jar")))
            .collect(java.util.stream.Collectors.toList());
        UpdatePlan plan = plan(artifacts);
        UpdateTransaction transaction = new UpdateTransaction(new LibraryPaths(data).getTransactions(),
            new ArtifactVerifier(1024), plugins);
        new UpdatePlanStager(PlatformType.BUKKIT, new LibraryPaths(data), http, transaction, 1024, true,
            id -> choice, id -> installed).stage(UUID.randomUUID(), plan,
            Collections.singletonMap(product, new InstalledUpdateTarget(current, plugins.resolve("update"))));
        return downloaded[0];
    }

    private UpdatePlan plan(List<ArtifactDescriptor> artifacts) {
        ProductRelease release = new ProductRelease(product, version, UpdateChannel.STABLE, new ApiVersionRange(1, 1), null,
            Collections.emptyList(), null, artifacts, Collections.emptyList(), null, null, "");
        return new UpdatePlan(1, Collections.singletonList(new ProductChange(product, SemanticVersion.parse("1.0.0"), version)),
            Collections.singletonList(release));
    }

    private void stage(String hash, Long size) throws IOException {
        stage(hash, size, "https://github.com/example/repo/releases/download/v2/library-new.jar");
    }

    private void stage(String hash, Long size, String url) throws IOException {
        Path plugins = directory.resolve("plugins");
        Path data = plugins.resolve("pnLibrary");
        Files.createDirectories(data);
        Path current = plugins.resolve("library.jar");
        Files.write(current, new byte[] {9});
        TrustedHttpClient http = new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            @Override
            public <T> T stream(URI uri, long maximumBytes, StreamReader<T> reader) throws IOException {
                downloads.incrementAndGet();
                return reader.read(new ByteArrayInputStream(bytes));
            }
        };
        ArtifactDescriptor artifact = new ArtifactDescriptor("library-new.jar", PlatformType.BUKKIT, 8, null, size, hash,
            URI.create(url));
        UpdateTransaction transaction = new UpdateTransaction(new LibraryPaths(data).getTransactions(),
            new ArtifactVerifier(1024), plugins);
        new UpdatePlanStager(PlatformType.BUKKIT, new LibraryPaths(data), http, transaction, 1024).stage(UUID.randomUUID(),
            plan(Collections.singletonList(artifact)),
            Collections.singletonMap(product, new InstalledUpdateTarget(current, plugins.resolve("update"))));
    }

    private String digest() {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String repeat(String text, int times) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < times; index++) {
            result.append(text);
        }
        return result.toString();
    }
}
