package org.pnfolder.pnlibrary.updates.engine.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrustedHttpClientTest {
    private final TrustedHttpClient client = new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1));

    @Test
    @DisplayName("construction leaves JVM networking properties untouched")
    void constructionLeavesJvmNetworkingPropertiesUntouched() {
        List<String> keys = Arrays.asList("java.net.preferIPv4Stack", "java.net.preferIPv4Addresses");
        String[] before = {System.getProperty(keys.get(0)), System.getProperty(keys.get(1))};

        new TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertEquals(Arrays.asList(before), Arrays.asList(System.getProperty(keys.get(0)), System.getProperty(keys.get(1))));
    }

    @Test
    @DisplayName("rejects non-HTTPS and credential-bearing URLs")
    void rejectsNonHttpsAndCredentialBearingUrls() {
        assertThrows(IllegalArgumentException.class, () -> client.validate(URI.create("http://example.org/file")));
        assertThrows(IllegalArgumentException.class, () -> client.validate(URI.create("https://user@example.org/file")));
    }

    @Test
    @DisplayName("bounded read rejects oversized bodies")
    void boundedReadRejectsOversizedBodies() throws IOException {
        assertArrayEquals(new byte[] {1, 2}, client.readBounded(new ByteArrayInputStream(new byte[] {1, 2}), 2));
        assertThrows(IllegalArgumentException.class,
            () -> client.readBounded(new ByteArrayInputStream(new byte[] {1, 2, 3}), 2));
    }
}
