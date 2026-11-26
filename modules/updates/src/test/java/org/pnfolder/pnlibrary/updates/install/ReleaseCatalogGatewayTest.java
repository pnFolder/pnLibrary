package org.pnfolder.pnlibrary.updates.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.updates.request.CatalogSource;
import org.pnfolder.pnlibrary.updates.engine.catalog.RefreshMode;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalog;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalogClient;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalogStore;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;

class ReleaseCatalogGatewayTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("single request thread does not deadlock while catalog client uses its own executor")
    void singleRequestThreadDoesNotDeadlockWhileCatalogClientUsesItsOwnExecutor() {
        ExecutorService catalogExecutor = Executors.newSingleThreadExecutor();
        ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
        byte[] catalogBytes = catalogJson().getBytes(StandardCharsets.UTF_8);
        TrustedHttpClient http = new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            @Override
            public byte[] get(URI uri, int maximumBytes) {
                if (uri.getHost().equals("api.github.com")) {
                    return ("{\"content\":\"" + Base64.getEncoder().encodeToString(catalogBytes) + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                }
                return catalogBytes;
            }
        };
        ReleaseCatalogClient client = new ReleaseCatalogClient(http,
            new ReleaseCatalogStore(temporaryDirectory.resolve("catalog")), catalogExecutor, Duration.ofMinutes(30));
        ReleaseCatalogGateway gateway = new ReleaseCatalogGateway(client, http, requestExecutor);

        try {
            AtomicReference<ReleaseCatalog> loaded = new AtomicReference<>();
            // A real deadlock hangs forever; a generous bound only guards against that, not against a loaded CI
            // runner's thread-scheduling jitter across the two single-thread executors used here.
            assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
                loaded.set(gateway.load(new CatalogSource("owner", "example"), RefreshMode.FORCE_REMOTE)));
            ReleaseCatalog catalog = loaded.get();
            assertEquals("example", catalog.getProduct());
            assertEquals("1.0.0", catalog.getReleases().get(0).getVersion().toString());
        } finally {
            requestExecutor.shutdownNow();
            catalogExecutor.shutdownNow();
            // The catalog client may still be writing its cache file; JUnit deletes the temporary directory next,
            // so wait until both threads have actually stopped instead of racing that cleanup.
            awaitTermination(requestExecutor);
            awaitTermination(catalogExecutor);
        }
    }

    private static void awaitTermination(ExecutorService executor) {
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("reads the catalog from the requested branch and folder and names the address when it is missing")
    void readsTheCatalogFromTheRequestedBranchAndFolderAndNamesTheAddressWhenItIsMissing() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<String> requested = new CopyOnWriteArrayList<>();
        TrustedHttpClient http = new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            @Override
            public byte[] get(URI uri, int maximumBytes) throws IOException {
                requested.add(uri.toString());
                throw new IOException("HTTP 404");
            }
        };
        ReleaseCatalogClient client = new ReleaseCatalogClient(http,
            new ReleaseCatalogStore(temporaryDirectory.resolve("branch")), executor, Duration.ofMinutes(30));
        try {
            Exception error = assertThrows(Exception.class, () -> new ReleaseCatalogGateway(client, http, executor)
                .load(new CatalogSource("PnFolder", "pnCases", "master", "updates"), RefreshMode.FORCE_REMOTE));
            String raw = "https://raw.githubusercontent.com/PnFolder/pnCases/refs/heads/master/updates/releases.json";
            assertTrue(requested.contains(raw), requested.toString());
            boolean named = false;
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                named |= cause.getMessage() != null && cause.getMessage().contains(raw);
            }
            assertTrue(named);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("jsDelivr is asked only when raw and api both fail")
    void jsDelivrIsAskedOnlyWhenRawAndApiBothFail() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<String> requested = new CopyOnWriteArrayList<>();
        byte[] fresh = catalogJson().getBytes(StandardCharsets.UTF_8);
        byte[] stale = catalogJson().replace("1.0.0", "0.9.0").getBytes(StandardCharsets.UTF_8);
        try {
            CatalogSource source = new CatalogSource("owner", "example");
            ReleaseCatalog ok = gateway(true, executor, requested, fresh, stale).load(source, RefreshMode.FORCE_REMOTE);
            assertEquals("1.0.0", ok.getReleases().get(0).getVersion().toString());
            assertFalse(requested.contains("cdn.jsdelivr.net"), requested.toString());

            ReleaseCatalog fallback = gateway(false, executor, requested, fresh, stale).load(source, RefreshMode.FORCE_REMOTE);
            assertEquals("0.9.0", fallback.getReleases().get(0).getVersion().toString());
        } finally {
            executor.shutdownNow();
        }
    }

    private ReleaseCatalogGateway gateway(boolean githubWorks, ExecutorService executor, List<String> requested,
                                          byte[] fresh, byte[] stale) {
        TrustedHttpClient http = new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            @Override
            public byte[] get(URI uri, int maximumBytes) throws IOException {
                requested.add(uri.getHost());
                if (uri.getHost().equals("cdn.jsdelivr.net")) {
                    return stale;
                }
                if (!githubWorks) {
                    throw new IOException("HTTP 503");
                }
                if (uri.getHost().equals("api.github.com")) {
                    return ("{\"content\":\"" + Base64.getEncoder().encodeToString(fresh) + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
                }
                return fresh;
            }
        };
        ReleaseCatalogStore store = new ReleaseCatalogStore(temporaryDirectory.resolve("cdn-" + githubWorks));
        return new ReleaseCatalogGateway(new ReleaseCatalogClient(http, store, executor, Duration.ofMinutes(30)), http,
            executor);
    }

    private static String catalogJson() {
        return "{\n"
            + "  \"schema\": 1,\n"
            + "  \"product\": \"example\",\n"
            + "  \"releases\": [\n"
            + "    {\n"
            + "      \"version\": \"1.0.0\",\n"
            + "      \"channel\": \"stable\",\n"
            + "      \"description\": \"test\",\n"
            + "      \"publishedAt\": \"2026-10-03T00:00:00Z\",\n"
            + "      \"api\": { \"minimum\": 1, \"maximum\": 1 },\n"
            + "      \"artifacts\": [\n"
            + "        {\n"
            + "          \"file\": \"example-1.0.0-bukkit-java8.jar\",\n"
            + "          \"platform\": \"BUKKIT\",\n"
            + "          \"java\": { \"minimum\": 8 },\n"
            + "          \"url\": \"https://example.com/example.jar\"\n"
            + "        }\n"
            + "      ]\n"
            + "    }\n"
            + "  ]\n"
            + "}\n";
    }
}
