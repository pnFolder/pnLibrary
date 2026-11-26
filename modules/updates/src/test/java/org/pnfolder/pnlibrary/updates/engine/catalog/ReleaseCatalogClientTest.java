package org.pnfolder.pnlibrary.updates.engine.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;

class ReleaseCatalogClientTest {
    @TempDir
    Path directory;

    @Test
    @DisplayName("loads catalog and reuses fresh cache")
    void loadsCatalogAndReusesFreshCache() {
        URI source = URI.create("https://example.org/.pnlibrary/releases.json");
        AtomicInteger calls = new AtomicInteger();
        TrustedHttpClient http = new TrustedHttpClient(Duration.ZERO, Duration.ZERO) {
            @Override
            public byte[] get(URI uri, int maximumBytes) throws IOException {
                calls.incrementAndGet();
                return ("{\"schema\":1,\"product\":\"demo\",\"releases\":[{\"version\":\"1.0.0\",\n"
                    + "\"channel\":\"stable\",\"description\":\"Demo\",\"publishedAt\":\"2026-09-29T12:00:00Z\",\n"
                    + "\"api\":{\"minimum\":1,\"maximum\":1},\"artifacts\":[{\"file\":\"demo.jar\",\n"
                    + "\"platform\":\"VELOCITY\",\"platformApi\":{\"minimum\":\"3.3.0\"},\n"
                    + "\"java\":{\"minimum\":17},\"url\":\"https://example.org/demo.jar\"}]}]}")
                    .getBytes(StandardCharsets.UTF_8);
            }
        };
        Executor direct = Runnable::run;
        ReleaseCatalogClient client = new ReleaseCatalogClient(http, new ReleaseCatalogStore(directory), direct, Duration.ofHours(1));
        assertEquals("1.0.0", client.load(source).join().getReleases().get(0).getVersion().toString());
        assertEquals("1.0.0", client.load(source).join().getReleases().get(0).getVersion().toString());
        assertEquals(1, calls.get());
    }
}
