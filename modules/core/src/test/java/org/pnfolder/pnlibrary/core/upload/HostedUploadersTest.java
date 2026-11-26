package org.pnfolder.pnlibrary.core.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HostedUploadersTest {
    @Test
    void emptyAnswerGivesAClearMessage() {
        IOException error = assertThrows(IOException.class, () -> UploadedLink.parse("Catbox", "", "files.catbox.moe"));
        assertTrue(error.getMessage().contains("empty answer"));
    }

    @Test
    void foreignHostAndPlainHttpAreRejected() {
        assertThrows(IOException.class, () -> UploadedLink.parse("Litterbox", "https://evil.example/a.txt", "litter.catbox.moe"));
        assertThrows(IOException.class, () -> UploadedLink.parse("Litterbox", "http://litter.catbox.moe/a.txt", "litter.catbox.moe"));
        assertThrows(IOException.class, () -> UploadedLink.parse("Litterbox", "Error: bad", "litter.catbox.moe"));
    }

    @Test
    void goodLinkKeepsItsFileName() throws IOException {
        URI uri = UploadedLink.parse("Litterbox", "https://litter.catbox.moe/7gc8i0.txt", "litter.catbox.moe");
        assertEquals("7gc8i0.txt", UploadedLink.lastSegment(uri));
    }

    @Test
    void uploadersRefuseInsecureEndpoints() {
        URI http = URI.create("http://example.org/x");
        assertThrows(IllegalArgumentException.class, () -> new LitterboxUploader(http));
        assertThrows(IllegalArgumentException.class, () -> new GofileUploader(http));
        assertThrows(IllegalArgumentException.class, () -> new CatboxUploader(http));
    }

    @Test
    void gofileReadsServerAndDownloadPage() throws IOException {
        String servers = "{\"status\":\"ok\",\"data\":{\"servers\":[{\"name\":\"store-na-phx-5\",\"zone\":\"na\"}]}}";
        assertEquals("store-na-phx-5", GofileUploader.serverName(servers));
        assertEquals("https://store-na-phx-5.gofile.io/contents/uploadfile",
            GofileUploader.uploadEndpoint("store-na-phx-5").toString());
        assertEquals("https://gofile.io/d/TaPoi3KP",
            GofileUploader.downloadPage("{\"data\":{\"downloadPage\":\"https://gofile.io/d/TaPoi3KP\"},\"status\":\"ok\"}"));
        assertThrows(IOException.class, () -> GofileUploader.serverName("{\"data\":{\"servers\":[{\"name\":\"evil.com/x\"}]}}"));
        assertThrows(IOException.class, () -> GofileUploader.serverName("<html>"));
    }

    @Test
    void chainListsEveryFailedPlatform() {
        UploadProvider a = new FailingProvider("catbox", "empty answer");
        UploadProvider b = new FailingProvider("litterbox", "HTTP 503");
        UploadProviderChain chain = new UploadProviderChain(Arrays.asList(a, b));
        IOException error = assertThrows(IOException.class, () -> chain.upload("x"));
        assertTrue(error.getMessage().contains("catbox: empty answer"));
        assertTrue(error.getMessage().contains("litterbox: HTTP 503"));
    }

    private static final class FailingProvider implements UploadProvider {
        private final String id;
        private final String reason;

        FailingProvider(String id, String reason) {
            this.id = id;
            this.reason = reason;
        }

        @Override
        public String getBackendId() {
            return id;
        }

        @Override
        public UploadReceipt upload(String payload) throws IOException {
            throw new IOException(reason);
        }
    }
}
