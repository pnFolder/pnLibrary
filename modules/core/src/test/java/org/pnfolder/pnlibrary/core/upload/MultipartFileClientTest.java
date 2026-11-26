package org.pnfolder.pnlibrary.core.upload;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultipartFileClientTest {
    private final URI https = URI.create("https://files.example/upload");

    private String post(Path dir, URI endpoint, Map<String, String> fields, String fileField, String contentType)
        throws IOException {
        Path file = Files.write(dir.resolve("r.zip"), new byte[] {1});
        return MultipartFileClient.post(endpoint, fields, fileField, file, contentType);
    }

    @Test
    void anInsecureEndpointIsRefusedBeforeAnyConnection(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
            () -> post(dir, URI.create("http://files.example/upload"), Collections.emptyMap(), "file",
                "application/zip"));
    }

    @Test
    void unsafeFieldNamesAndHeaderValuesAreRefused(@TempDir Path dir) {
        Map<String, String> none = Collections.emptyMap();
        assertThrows(IllegalArgumentException.class, () -> post(dir, https, none, "file\"; x=\"", "application/zip"));
        assertThrows(IllegalArgumentException.class,
            () -> post(dir, https, Collections.singletonMap("a b", "1"), "file", "application/zip"));
        assertThrows(IllegalArgumentException.class,
            () -> post(dir, https, none, "file", "text/plain\r\nX-Evil: 1"));
        assertThrows(IllegalArgumentException.class, () -> post(dir, https, none, "file", " "));
    }
}
