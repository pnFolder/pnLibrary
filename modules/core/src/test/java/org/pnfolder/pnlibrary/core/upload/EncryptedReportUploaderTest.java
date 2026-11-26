package org.pnfolder.pnlibrary.core.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncryptedReportUploaderTest {
    private HttpServer server;
    private final List<String> requests = new ArrayList<String>();
    private int status = 200;
    private String response = "";

    private final String id = "abcdefgh12345";
    private final String token = repeat("t", 32);

    private static String repeat(String text, int times) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < times; i++) {
            result.append(text);
        }
        return result.toString();
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " auth="
                + exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } else {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private EncryptedReportUploader uploader() {
        return new EncryptedReportUploader(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/store"),
            URI.create("https://reports.example/view/"), false);
    }

    private UploadReceipt receipt(String id, String token) {
        return new UploadReceipt(URI.create("https://reports.example/view/" + id), id, token, 0, 0, "encrypted");
    }

    @Test
    void aSuccessfulUploadReturnsAReceiptResolvedAgainstThePublicBase() throws Exception {
        response = "{\"success\":true,\"id\":\"" + id + "\",\"deleteToken\":\"" + token + "\",\"created\":10,\"expires\":20}";

        UploadReceipt result = uploader().upload("{}");

        assertEquals(URI.create("https://reports.example/view/" + id), result.getLink());
        assertEquals(id, result.getId());
        assertEquals(20L, result.getExpiresEpochSeconds());
        assertEquals("POST /store auth=null", requests.get(0));
        assertEquals(1, requests.size());
    }

    @Test
    void aRejectedMalformedOrUnsafeStorageResponseIsAnIOException() {
        for (String body : Arrays.asList(
            "{\"success\":false}",
            "not json",
            "{\"success\":true,\"id\":\"../evil\",\"deleteToken\":\"" + token + "\"}",
            "{\"success\":true,\"id\":\"" + id + "\"}")) {
            response = body;
            assertThrows(IOException.class, () -> uploader().upload("{}"));
        }
    }

    @Test
    void anHttpErrorAndAnOversizedResponseAreIOExceptions() {
        status = 500;
        assertThrows(IOException.class, () -> uploader().upload("{}"));

        status = 200;
        response = repeat("x", 20_000);
        assertThrows(IOException.class, () -> uploader().upload("{}"));
    }

    @Test
    void deletionSendsTheBearerTokenAndTreatsAnAbsentReportAsDeleted() throws Exception {
        status = 204;
        assertTrue(uploader().delete(receipt(id, token)));
        assertEquals("DELETE /store/" + id + " auth=Bearer " + token, requests.get(0));

        status = 404;
        assertTrue(uploader().delete(receipt(id, token)));

        status = 500;
        assertFalse(uploader().delete(receipt(id, token)));
    }

    @Test
    void deletionRefusesIdsAndTokensThatWereNotIssuedByTheStorage() throws Exception {
        status = 204;

        assertFalse(uploader().delete(receipt("../../admin", token)));
        assertFalse(uploader().delete(receipt(id, "short")));
        assertFalse(uploader().delete(receipt(id, "")));
        assertTrue(requests.isEmpty());
    }

    @Test
    void aReportFileIsUploadedAsItsBytes(@TempDir Path dir) throws Exception {
        response = "{\"success\":true,\"id\":\"" + id + "\",\"deleteToken\":\"" + token + "\"}";
        Path file = Files.write(dir.resolve("report.bin"), new byte[] {1, 2, 3});

        assertEquals(id, uploader().uploadFile(file, "application/octet-stream").getId());
    }

    @Test
    void nonHttpsEndpointsAreRejectedUnlessExplicitlyAllowed() {
        assertThrows(IllegalArgumentException.class,
            () -> new EncryptedReportUploader(URI.create("http://host/store"), URI.create("https://host/view/")));
        assertThrows(IllegalArgumentException.class,
            () -> new EncryptedReportUploader(URI.create("https://host/store"), URI.create("https://host/view")));
    }
}
