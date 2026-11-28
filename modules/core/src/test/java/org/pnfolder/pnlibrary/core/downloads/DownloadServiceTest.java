package org.pnfolder.pnlibrary.core.downloads;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration;
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot;
import org.pnfolder.pnlibrary.api.downloads.DownloadState;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths;

class DownloadServiceTest {
    @TempDir
    Path directory;

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static List<PluginDependency> only(ExternalPluginDependency dependency) {
        return Arrays.asList(dependency);
    }

    private static ExternalPluginDependency vault(String url) {
        return ExternalPluginDependency.builder("Vault", "1.7.3")
            .url(url)
            .downloadPolicy(DownloadPolicy.AUTOMATIC)
            .build();
    }

    private FileDownloads dataRequest(String directoryName, String id, String path) {
        return FileDownloads.builder().dataDirectory(directory.resolve(directoryName))
            .file(id, file -> file.url("https://example.org/" + path)
                .destination(DownloadDestination.DATA_FOLDER, path)).build();
    }

    @Test
    void installsOrdinaryFileOnlyInsideDeclaredDataDirectory() throws Exception {
        byte[] payload = bytes("safe-data");
        FileDownloads request = FileDownloads.builder().dataDirectory(directory.resolve("plugin-data"))
            .file("data", file -> file.url("https://example.org/data.bin")
                .destination(DownloadDestination.DATA_FOLDER, "models/data.bin")).build();
        try (DownloadService service = manager(payload, true, null, null, Collections.emptyMap(),
            PlatformType.BUKKIT)) {
            service.install(request, request.getFiles().get(0));
        }

        assertArrayEquals(payload, Files.readAllBytes(directory.resolve("plugin-data/models/data.bin")));
    }

    @Test
    void manualDownloadWorksWhenAutomaticPolicyIsDisabled() throws Exception {
        byte[] payload = bytes("manual");
        FileDownloads request = dataRequest("manual-data", "manual", "manual.bin");
        try (DownloadService service = manager(payload, false, null, null, Collections.emptyMap(),
            PlatformType.BUKKIT)) {
            DownloadRegistration registration = service.register(new Object(), request);
            assertFalse(Files.exists(directory.resolve("manual-data/manual.bin")));
            List<DownloadSnapshot> result = registration.downloadNow().toCompletableFuture().join();
            assertEquals(DownloadState.STAGED, result.get(0).getState());
            assertArrayEquals(payload, Files.readAllBytes(directory.resolve("manual-data/manual.bin")));
            registration.close();
            assertTrue(registration.isClosed());
            assertThrows(UnsupportedOperationException.class, () -> registration.snapshots().clear());
        }
    }

    @Test
    void automaticExternalDependencyIsStagedInServerUpdateDirectory() throws Exception {
        byte[] payload = pluginJar("Vault", "1.7.3");

        try (DownloadService service = manager(payload, true, null, null, Collections.emptyMap(),
            PlatformType.BUKKIT)) {
            DownloadRegistration registration = service.registerDependencies(new Object(), "main",
                only(vault("https://example.org/download")));
            registration.downloadNow().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertArrayEquals(payload, Files.readAllBytes(directory.resolve("plugins/update/Vault.jar")));
        }
    }

    @Test
    void dependencyValidationUsesTheDescriptorOfTheActivePlatform() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", "name: Vault\nversion: 1.7.3\nmain: example.Main\n");
        entries.put("bungee.yml", "name: DifferentPlugin\nversion: 1.7.3\nmain: example.Main\n");

        try (DownloadService service = manager(pluginJar(entries), true, null, null,
            Collections.emptyMap(), PlatformType.BUNGEECORD)) {
            DownloadRegistration registration = service.registerDependencies(new Object(), "main",
                only(vault("https://example.org/Vault.jar")));
            assertEquals(DownloadState.FAILED,
                registration.downloadNow().toCompletableFuture().get(5, TimeUnit.SECONDS).get(0).getState());
        }
    }

    @Test
    void dependencyDownloadRejectsAJarWhoseDeclaredPluginIdentityDoesNotMatch() throws Exception {
        try (DownloadService service = manager(pluginJar("NotVault", "1.7.3"), true, null, null,
            Collections.emptyMap(), PlatformType.BUKKIT)) {
            DownloadRegistration registration = service.registerDependencies(new Object(), "main",
                only(vault("https://example.org/Vault.jar")));
            List<DownloadSnapshot> snapshots = registration.downloadNow().toCompletableFuture().join();
            assertEquals(DownloadState.FAILED, snapshots.get(0).getState());
            assertFalse(Files.exists(directory.resolve("plugins/update/Vault.jar")));
        }
    }

    @Test
    void dependencyAtMinimumVersionIsNotDownloadedAgain() throws Exception {
        try (DownloadService service = manager(pluginJar("Vault", "1.7.3"), true, null, null,
            Collections.singletonMap("Vault", "1.7.3"), PlatformType.BUKKIT)) {
            assertNull(service.registerDependencies(new Object(), "main", only(vault("https://example.org/Vault.jar"))));
        }
    }

    @Test
    void doesNotPublishFirstItemWhenALaterItemFailsVerification() throws Exception {
        FileDownloads request = FileDownloads.builder().dataDirectory(directory.resolve("plugin-data"))
            .file("data", file -> file.url("https://example.org/data.bin")
                .destination(DownloadDestination.DATA_FOLDER, "data.bin"))
            .file("broken", file -> file.url("https://example.org/broken.bin")
                .integrity(1, repeatZero())
                .destination(DownloadDestination.DATA_FOLDER, "broken.bin"))
            .build();

        try (DownloadService service = manager(bytes("not-a-jar"), true, null, null,
            Collections.emptyMap(), PlatformType.BUKKIT)) {
            assertThrows(IllegalArgumentException.class, () -> service.installBatch(request, request.getFiles()));
        }

        assertFalse(Files.exists(directory.resolve("plugin-data/data.bin")));
    }

    @Test
    void closingManagerBeforePublicationDoesNotPublishPreparedFiles() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FileDownloads request = dataRequest("plugin-data", "data", "data.bin");
        try (DownloadService service = manager(bytes("late-download"), true, entered, release,
            Collections.emptyMap(), PlatformType.BUKKIT)) {
            DownloadRegistration registration = service.register(new Object(), request);
            java.util.concurrent.CompletableFuture<List<DownloadSnapshot>> future =
                registration.downloadNow().toCompletableFuture();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            service.close();
            release.countDown();

            assertTrue(future.isCompletedExceptionally());
            assertFalse(Files.exists(directory.resolve("plugin-data/data.bin")));
        }
    }

    private static String repeatZero() {
        char[] zeros = new char[64];
        Arrays.fill(zeros, '0');
        return new String(zeros);
    }

    private DownloadService manager(byte[] bytes, boolean automatic, CountDownLatch entered, CountDownLatch release,
                                    Map<String, String> installed, PlatformType platformType) {
        PlatformAdapter platform = (PlatformAdapter) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {PlatformAdapter.class}, (proxy, method, arguments) -> {
                switch (method.getName()) {
                    case "getType":
                        return platformType;
                    case "getArtifactId":
                        return platformType.name().toLowerCase(Locale.ROOT);
                    case "getDescriptorFileName":
                        return descriptorOf(platformType);
                    case "installedPlugins":
                        return installed;
                    case "details":
                        return Collections.emptyMap();
                    default:
                        return null;
                }
            });
        TrustedHttpClient http = new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            @Override
            public byte[] get(URI uri, int maximumBytes) throws IOException {
                if (entered != null) {
                    entered.countDown();
                }
                try {
                    if (release != null) {
                        release.await(5, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
                if (bytes.length > maximumBytes) {
                    throw new IllegalArgumentException("Failed requirement.");
                }
                return bytes;
            }
        };
        LibraryPaths paths = new LibraryPaths(directory.resolve("plugins/pnLibrary"));
        return new DownloadService(platform, platform, platform, platform, platform, paths,
            DownloadConfiguration::new, http);
    }

    private static String descriptorOf(PlatformType type) {
        switch (type) {
            case BUKKIT:
                return "plugin.yml";
            case BUNGEECORD:
                return "bungee.yml";
            case VELOCITY:
                return "velocity-plugin.json";
            default:
                throw new IllegalStateException("not a real platform");
        }
    }

    private static byte[] pluginJar(String name, String version) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("plugin.yml", "name: " + name + "\nversion: " + version + "\nmain: example.Main\n");
        return pluginJar(entries);
    }

    private static byte[] pluginJar(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JarOutputStream archive = new JarOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                archive.putNextEntry(new JarEntry(entry.getKey()));
                archive.write(bytes(entry.getValue()));
                archive.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
