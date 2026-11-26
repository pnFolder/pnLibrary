package org.pnfolder.pnlibrary.localization.internal;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.LocaleTranslations;
import org.pnfolder.pnlibrary.localization.MinecraftLocalization;
import org.pnfolder.pnlibrary.localization.TranslationBundle;
import org.pnfolder.pnlibrary.localization.TranslationException;
import org.pnfolder.pnlibrary.localization.TranslationRequest;
import org.pnfolder.pnlibrary.localization.TranslationSource;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationServiceTest {
    private static final String SWORD_KEY = "item.minecraft.diamond_sword";
    private static final String RU_SWORD = "Алмазный меч";

    @TempDir
    Path directory;

    private static TranslationRequest russian() {
        return TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build();
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static Throwable failure(CompletionStage<?> stage) {
        return assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()).getCause();
    }

    private static boolean hasCorruptSibling(Path path) throws IOException {
        try (Stream<Path> files = Files.list(path.getParent())) {
            return files.anyMatch(file -> file.getFileName().toString().contains(".corrupt-"));
        }
    }

    private static List<String> englishAndRussian() {
        return Arrays.asList("en_us", "ru_ru");
    }

    @Test
    @DisplayName("loads only requested locale and reuses memory")
    void loadsOnlyRequestedLocaleAndReusesMemory() {
        Fixture fixture = new Fixture(0, null);
        try (DefaultMinecraftLocalization service = service(fixture)) {
            TranslationRequest request = russian();
            LocaleTranslations first = join(service.load(request)).locale("ru_ru");
            LocaleTranslations second = join(service.load(request)).locale("ru_ru");
            assertEquals(RU_SWORD, first.translate(SWORD_KEY).get());
            assertEquals(TranslationSource.MEMORY, second.getMetadata().getSource());
            assertSame(first.keys(), second.keys());
            int english = 0;
            for (URI uri : fixture.calls.keySet()) {
                if (uri.toString().contains("en_us")) {
                    english++;
                }
            }
            assertEquals(0, english);
            assertEquals(1, fixture.assetDownloads);
        }
    }

    @Test
    @DisplayName("reuses version metadata and asset index across operations")
    void reusesVersionMetadataAndAssetIndexAcrossOperations() {
        Fixture fixture = new Fixture(0, null);
        try (DefaultMinecraftLocalization service = service(fixture)) {
            TranslationRequest request = russian();
            assertEquals(englishAndRussian(), join(service.availableLocales(MinecraftVersion.V1_21_4)));
            join(service.load(request));
            join(service.refresh(request));
            assertEquals(Integer.valueOf(1), fixture.calls.get(fixture.versionUri));
            assertEquals(Integer.valueOf(1), fixture.calls.get(fixture.indexUri));
        }
    }

    @Test
    @DisplayName("shares concurrent same locale download")
    void sharesConcurrentSameLocaleDownload() {
        Fixture fixture = new Fixture(75, null);
        try (DefaultMinecraftLocalization service = service(fixture)) {
            TranslationRequest request = russian();
            CompletableFuture<TranslationBundle> first = service.load(request).toCompletableFuture();
            CompletableFuture<TranslationBundle> second = service.load(request).toCompletableFuture();
            first.join();
            second.join();
            assertEquals(1, fixture.assetDownloads);
        }
    }

    @Test
    @DisplayName("completed in flight entry does not suppress explicit refresh")
    void completedInFlightEntryDoesNotSuppressExplicitRefresh() {
        Fixture fixture = new Fixture(0, null);
        try (DefaultMinecraftLocalization service = service(fixture)) {
            TranslationRequest request = russian();
            join(service.load(request));
            join(service.refresh(request));
            assertEquals(2, fixture.assetDownloads);
        }
    }

    @Test
    @DisplayName("discovers supported release versions and locales")
    void discoversSupportedReleaseVersionsAndLocales() {
        Fixture fixture = new Fixture(0, null);
        try (DefaultMinecraftLocalization service = service(fixture)) {
            assertEquals(Collections.singletonList(MinecraftVersion.V1_21_4), join(service.availableVersions()));
            assertEquals(englishAndRussian(), join(service.availableLocales(MinecraftVersion.V1_21_4)));
        }
    }

    @Test
    @DisplayName("reopens a downloaded locale from disk without network")
    void reopensADownloadedLocaleFromDiskWithoutNetwork() {
        TranslationRequest request = russian();
        try (DefaultMinecraftLocalization service = service(new Fixture(0, null))) {
            join(service.load(request));
        }
        try (DefaultMinecraftLocalization service = service(new FailingClient())) {
            LocaleTranslations locale = join(service.load(request)).locale("ru_ru");
            assertEquals(TranslationSource.DISK, locale.getMetadata().getSource());
            assertEquals(RU_SWORD, locale.translate(SWORD_KEY).get());
        }
    }

    @Test
    @DisplayName("reopens version asset catalog from disk without network")
    void reopensVersionAssetCatalogFromDiskWithoutNetwork() {
        try (DefaultMinecraftLocalization service = service(new Fixture(0, null))) {
            assertEquals(englishAndRussian(), join(service.availableLocales(MinecraftVersion.V1_21_4)));
        }
        try (DefaultMinecraftLocalization service = service(new FailingClient())) {
            assertEquals(englishAndRussian(), join(service.availableLocales(MinecraftVersion.V1_21_4)));
        }
    }

    @Test
    @DisplayName("quarantines structurally corrupt version catalog and downloads a clean copy")
    void quarantinesStructurallyCorruptVersionCatalogAndDownloadsACleanCopy() throws IOException {
        Path path = directory.resolve("minecraft/versions/1.21.4.json");
        Files.createDirectories(path.getParent());
        Files.write(path, "{}".getBytes());
        try (DefaultMinecraftLocalization service = service(new Fixture(0, null))) {
            assertEquals(englishAndRussian(), join(service.availableLocales(MinecraftVersion.V1_21_4)));
        }
        assertTrue(hasCorruptSibling(path));
    }

    @Test
    @DisplayName("quarantines corrupt manifests even while their cache is fresh")
    void quarantinesCorruptManifestsEvenWhileTheirCacheIsFresh() throws IOException {
        Path path = directory.resolve("minecraft/version-manifest.json");
        Files.createDirectories(path.getParent());
        for (String corrupt : Arrays.asList("not-json", "{}", "{\"versions\":{}}", "{\"versions\":[null]}")) {
            Files.write(path, corrupt.getBytes());
            Fixture fixture = new Fixture(0, null);
            try (DefaultMinecraftLocalization service = service(fixture)) {
                assertEquals(Collections.singletonList(MinecraftVersion.V1_21_4), join(service.availableVersions()));
                assertEquals(Integer.valueOf(1), fixture.calls.get(MojangAssetResolver.MANIFEST));
            }
        }
        assertTrue(hasCorruptSibling(path));
    }

    @Test
    @DisplayName("malformed remote manifest does not replace a valid stale cache")
    void malformedRemoteManifestDoesNotReplaceAValidStaleCache() throws IOException {
        try (DefaultMinecraftLocalization service = service(new Fixture(0, null))) {
            join(service.availableVersions());
        }
        Path path = directory.resolve("minecraft/version-manifest.json");
        byte[] original = Files.readAllBytes(path);
        Files.setLastModifiedTime(path, FileTime.fromMillis(0));
        try (DefaultMinecraftLocalization service = service(new Fixture(0, "{}".getBytes()))) {
            assertEquals(Collections.singletonList(MinecraftVersion.V1_21_4), join(service.availableVersions()));
        }
        assertArrayEquals(original, Files.readAllBytes(path));
    }

    @Test
    @DisplayName("malformed remote manifest fails with a typed error and is not cached")
    void malformedRemoteManifestFailsWithATypedErrorAndIsNotCached() {
        try (DefaultMinecraftLocalization service = service(new Fixture(0, "{\"versions\":[null]}".getBytes()))) {
            Throwable error = failure(service.availableLocales(MinecraftVersion.V1_21_4));
            assertEquals(TranslationException.Reason.MALFORMED_DATA, ((TranslationException) error).getReason());
        }
        assertFalse(Files.exists(directory.resolve("minecraft/version-manifest.json")));
    }

    @Test
    @DisplayName("extracts embedded locale through streamed client archive and removes temporary file")
    void extractsEmbeddedLocaleThroughStreamedClientArchiveAndRemovesTemporaryFile() throws IOException {
        ClientArchiveFixture fixture = new ClientArchiveFixture();
        try (DefaultMinecraftLocalization service = service(fixture)) {
            TranslationRequest request =
                    TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("en_us").build();
            LocaleTranslations locale = join(service.load(request)).locale("en_us");
            assertEquals("Diamond Sword", locale.translate(SWORD_KEY).get());
        }
        assertTrue(fixture.streamed);
        Path temporary = directory.resolve("minecraft/temporary");
        if (Files.exists(temporary)) {
            try (Stream<Path> files = Files.list(temporary)) {
                assertTrue(!files.findAny().isPresent());
            }
        }
    }

    @Test
    @DisplayName("quarantines corrupt disk locale instead of serving it")
    void quarantinesCorruptDiskLocaleInsteadOfServingIt() throws IOException {
        Path path = directory.resolve("minecraft/translations/1.21.4/ru_ru.json");
        Files.createDirectories(path.getParent());
        Files.write(path, "not-json".getBytes());
        TranslationRequest request = russian();
        try (DefaultMinecraftLocalization service = service(new FailingClient())) {
            assertThrows(CompletionException.class, () -> service.load(request).toCompletableFuture().join());
        }
        assertTrue(hasCorruptSibling(path));
    }

    @Test
    @DisplayName("closed service rejects new operations")
    void closedServiceRejectsNewOperations() {
        DefaultMinecraftLocalization service = service(new Fixture(0, null));
        service.close();
        assertThrows(TranslationException.class, service::availableVersions);
    }

    @Test
    @DisplayName("rejected locale submissions fail asynchronously and allow load and refresh retries")
    void rejectedLocaleSubmissionsFailAsynchronouslyAndAllowLoadAndRefreshRetries() throws Exception {
        TranslationRequest request = russian();
        for (boolean refresh : new boolean[]{false, true}) {
            RejectOnceExecutor executor = new RejectOnceExecutor(null);
            try (DefaultMinecraftLocalization service = service(new Fixture(0, null), executor)) {
                CompletableFuture<TranslationBundle> rejected =
                        (refresh ? service.refresh(request) : service.load(request)).toCompletableFuture();
                assertTrue(rejected.isCompletedExceptionally());
                assertSame(executor.rejection, failure(rejected));

                CompletableFuture<TranslationBundle> retry =
                        (refresh ? service.refresh(request) : service.load(request)).toCompletableFuture();
                assertEquals(RU_SWORD, retry.get(5, TimeUnit.SECONDS).locale("ru_ru").translate(SWORD_KEY).get());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("callers sharing a rejected locale submission receive the same failure")
    void callersSharingARejectedLocaleSubmissionReceiveTheSameFailure() {
        final TranslationRequest request = russian();
        final AtomicReference<DefaultMinecraftLocalization> service = new AtomicReference<>();
        final AtomicReference<CompletableFuture<?>> shared = new AtomicReference<>();
        RejectOnceExecutor executor = new RejectOnceExecutor(() -> shared.set(service.get().load(request).toCompletableFuture()));
        try {
            service.set(service(new Fixture(0, null), executor));
            try (DefaultMinecraftLocalization opened = service.get()) {
                CompletableFuture<TranslationBundle> rejected = opened.load(request).toCompletableFuture();
                CompletableFuture<?> second = shared.get();
                assertTrue(rejected.isCompletedExceptionally());
                assertTrue(second.isCompletedExceptionally());
                assertSame(executor.rejection, failure(rejected));
                assertSame(executor.rejection, failure(second));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("rejected metadata submissions fail their returned futures")
    void rejectedMetadataSubmissionsFailTheirReturnedFutures() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.shutdown();
        try (DefaultMinecraftLocalization service = service(new Fixture(0, null), executor)) {
            List<CompletionStage<?>> operations = Arrays.asList(
                    service.availableVersions(), service.availableLocales(MinecraftVersion.V1_21_4));
            for (CompletionStage<?> operation : operations) {
                CompletableFuture<?> future = operation.toCompletableFuture();
                assertTrue(future.isCompletedExceptionally());
                assertInstanceOf(RejectedExecutionException.class, failure(future));
            }
        }
    }

    private DefaultMinecraftLocalization service(LocalizationHttpClient fixture) {
        return service(fixture, null, KeyboardLayouts.create());
    }

    private DefaultMinecraftLocalization service(LocalizationHttpClient fixture, ExecutorService executor) {
        return service(fixture, executor, KeyboardLayouts.create());
    }

    private DefaultMinecraftLocalization service(LocalizationHttpClient fixture, ExecutorService executor,
                                                 KeyboardLayouts layouts) {
        return new DefaultMinecraftLocalization(
                directory, Duration.ofHours(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
                4, 2, executor, fixture, layouts);
    }

    @Test
    @DisplayName("loaded tables search with the layouts of the service that loaded them")
    void loadedTablesSearchWithTheLayoutsOfTheServiceThatLoadedThem() throws Exception {
        KeyboardLayouts layouts = KeyboardLayouts.create();
        try (DefaultMinecraftLocalization service = service(new Fixture(0, null), null, layouts)) {
            LocaleTranslations table = join(service.load(russian())).locale("ru_ru");

            assertSame(layouts, service.getLayouts());
            assertSame(layouts, table.getLayouts());
            assertSame(layouts, table.keys().getLayouts());
        }
        layouts.register(KeyboardLayout.of("service-test", "abcdefghijklmnopqrstuvwxyz012345678")).close();
    }

    @Test
    @DisplayName("each service has its own layouts unless one is supplied to the builder")
    void eachServiceHasItsOwnLayoutsUnlessOneIsSuppliedToTheBuilder() {
        KeyboardLayouts shared = KeyboardLayouts.create();
        MinecraftLocalization first = MinecraftLocalization.builder().cacheDirectory(directory).build();
        MinecraftLocalization second = MinecraftLocalization.builder().cacheDirectory(directory).layouts(shared).build();
        try {
            assertSame(shared, second.getLayouts());
            assertNotSame(first.getLayouts(), second.getLayouts());
        } finally {
            second.close();
            first.close();
        }
    }

    @Test
    @DisplayName("close settles queued metadata and locale operations without shutting down supplied executor")
    void closeSettlesQueuedMetadataAndLocaleOperationsWithoutShuttingDownSuppliedExecutor() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        final CountDownLatch occupied = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            occupied.countDown();
            try {
                release.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(occupied.await(5, TimeUnit.SECONDS));
        try {
            DefaultMinecraftLocalization service = service(new Fixture(0, null), executor);
            List<CompletionStage<?>> operations = Arrays.asList(
                    service.availableVersions(), service.availableLocales(MinecraftVersion.V1_21_4),
                    service.load(russian()));
            service.close();
            for (CompletionStage<?> operation : operations) {
                Throwable error = failure(operation);
                assertEquals(TranslationException.Reason.CLOSED, ((TranslationException) error).getReason());
            }
            assertFalse(executor.isShutdown());
            release.countDown();
            assertEquals(Integer.valueOf(42), executor.submit(() -> 42).get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("close racing executor submission settles the accepted operation")
    void closeRacingExecutorSubmissionSettlesTheAcceptedOperation() {
        final AtomicReference<DefaultMinecraftLocalization> service = new AtomicReference<>();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {
            @Override
            public void execute(Runnable command) {
                service.get().close();
                super.execute(command);
            }
        };
        try {
            service.set(service(new Fixture(0, null), executor));
            CompletableFuture<List<MinecraftVersion>> future = service.get().availableVersions().toCompletableFuture();
            Throwable error = failure(future);
            assertEquals(TranslationException.Reason.CLOSED, ((TranslationException) error).getReason());
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class RejectOnceExecutor extends ThreadPoolExecutor {
        final RejectedExecutionException rejection = new RejectedExecutionException("Test executor rejected the submission");
        private final Runnable beforeRejection;
        private final AtomicBoolean rejectNext = new AtomicBoolean(true);

        RejectOnceExecutor(Runnable beforeRejection) {
            super(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
            this.beforeRejection = beforeRejection;
        }

        @Override
        public void execute(Runnable command) {
            if (rejectNext.compareAndSet(true, false)) {
                if (beforeRejection != null) {
                    beforeRejection.run();
                }
                throw rejection;
            }
            super.execute(command);
        }
    }

    private static String sha1(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(bytes);
            StringBuilder text = new StringBuilder();
            for (byte b : digest) {
                text.append(String.format(Locale.ROOT, "%02x", b));
            }
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static final class Fixture extends LocalizationHttpClient {
        final Map<URI, Integer> calls = new ConcurrentHashMap<>();
        volatile int assetDownloads = 0;
        private final long delayMillis;
        private final byte[] manifestOverride;
        private final byte[] language = ("{\"" + SWORD_KEY + "\":\"" + RU_SWORD + "\"}").getBytes(StandardCharsets.UTF_8);
        private final String hash = sha1(language);
        final URI versionUri = URI.create("https://piston-meta.mojang.com/v1/1.21.4.json");
        final URI indexUri = URI.create("https://piston-meta.mojang.com/v1/1.21.4-assets.json");
        private final URI assetUri = URI.create("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash);

        Fixture(long delayMillis, byte[] manifestOverride) {
            super(Duration.ZERO, Duration.ZERO);
            this.delayMillis = delayMillis;
            this.manifestOverride = manifestOverride;
        }

        @Override
        byte[] get(URI uri, int maximumBytes) throws IOException {
            calls.merge(uri, 1, (a, b) -> a + b);
            if (uri.equals(MojangAssetResolver.MANIFEST)) {
                if (manifestOverride != null) {
                    return manifestOverride;
                }
                return ("{\"versions\":[\n"
                        + "{\"id\":\"1.21.4\",\"type\":\"release\",\"url\":\"" + versionUri + "\"},\n"
                        + "{\"id\":\"99.1\",\"type\":\"release\",\"url\":\"https://piston-meta.mojang.com/v1/future.json\"},\n"
                        + "{\"id\":\"1.21.5-pre1\",\"type\":\"snapshot\",\"url\":\"https://piston-meta.mojang.com/v1/snapshot.json\"}\n"
                        + "]}").getBytes();
            }
            if (uri.equals(versionUri)) {
                return ("{\"assetIndex\":{\"url\":\"" + indexUri + "\"}}").getBytes();
            }
            if (uri.equals(indexUri)) {
                return ("{\"objects\":{\"minecraft/lang/ru_ru.json\":{\"hash\":\"" + hash + "\",\"size\":"
                        + language.length + "}}}").getBytes();
            }
            if (uri.equals(assetUri)) {
                assetDownloads++;
                if (delayMillis > 0) {
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }
                return language;
            }
            throw new IllegalStateException("Unexpected URI " + uri);
        }
    }

    private static final class FailingClient extends LocalizationHttpClient {
        FailingClient() {
            super(Duration.ZERO, Duration.ZERO);
        }

        @Override
        byte[] get(URI uri, int maximumBytes) throws IOException {
            throw new IOException("offline");
        }
    }

    private static final class ClientArchiveFixture extends LocalizationHttpClient {
        private final URI versionUri = URI.create("https://piston-meta.mojang.com/v1/client-version.json");
        private final URI indexUri = URI.create("https://piston-meta.mojang.com/v1/client-assets.json");
        private final URI clientUri = URI.create("https://piston-data.mojang.com/v1/client.jar");
        private final byte[] language = ("{\"" + SWORD_KEY + "\":\"Diamond Sword\"}").getBytes();
        private final byte[] archive = archive(language);
        private final String archiveHash = sha1(archive);
        volatile boolean streamed = false;

        ClientArchiveFixture() {
            super(Duration.ZERO, Duration.ZERO);
        }

        private static byte[] archive(byte[] language) {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (ZipOutputStream zip = new ZipOutputStream(output)) {
                    zip.putNextEntry(new ZipEntry("assets/minecraft/lang/en_us.json"));
                    zip.write(language);
                    zip.closeEntry();
                }
                return output.toByteArray();
            } catch (IOException error) {
                throw new IllegalStateException(error);
            }
        }

        @Override
        byte[] get(URI uri, int maximumBytes) throws IOException {
            if (uri.equals(MojangAssetResolver.MANIFEST)) {
                return ("{\"versions\":[{\"id\":\"1.21.4\",\"type\":\"release\",\"url\":\"" + versionUri + "\"}]}").getBytes();
            }
            if (uri.equals(versionUri)) {
                return ("{\n"
                        + "\"assetIndex\":{\"url\":\"" + indexUri + "\"},\n"
                        + "\"downloads\":{\"client\":{\"url\":\"" + clientUri + "\",\"sha1\":\"" + archiveHash
                        + "\",\"size\":" + archive.length + "}}\n}").getBytes();
            }
            if (uri.equals(indexUri)) {
                return "{\"objects\":{}}".getBytes();
            }
            if (uri.equals(clientUri)) {
                throw new IllegalStateException("Client archive must not be materialized through get()");
            }
            throw new IllegalStateException("Unexpected URI " + uri);
        }

        @Override
        long download(URI uri, int maximumBytes, Path destination) throws IOException {
            if (!uri.equals(clientUri)) {
                throw new IllegalStateException();
            }
            if (archive.length > maximumBytes) {
                throw new IllegalStateException();
            }
            streamed = true;
            Files.write(destination, archive);
            return archive.length;
        }
    }
}
