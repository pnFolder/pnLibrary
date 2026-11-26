package org.pnfolder.pnlibrary.core.upload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Uploads an encrypted report to file.io as a single-download fallback.
 *
 * <p>The provider requests a seven-day expiry and deletion after the first download. It intentionally does not
 * implement remote deletion because file.io supplies no deletion token through this API.
 */
public final class FileIoUploader implements UploadProvider {
    private static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;

    private final URI endpoint;

    public FileIoUploader() {
        this(URI.create("https://file.io"));
    }

    /**
     * @param endpoint HTTPS file.io endpoint
     * @throws IllegalArgumentException if {@code endpoint} is not HTTPS
     */
    public FileIoUploader(@NotNull URI endpoint) {
        UploadHttp.requireHttps(endpoint, "file.io endpoint");
        this.endpoint = endpoint;
    }

    @Override
    public @NotNull String getBackendId() {
        return "fileio";
    }

    @Override
    public @NotNull UploadReceipt upload(@NotNull String payload) throws IOException {
        throw new IOException("file.io requires a binary file");
    }

    @Override
    public @NotNull UploadReceipt uploadFile(@NotNull Path file, @NotNull String contentType) throws IOException {
        long size = Files.size(file);
        if (size < 1 || size > MAX_BYTES) {
            throw new IllegalArgumentException("file.io report size must be between 1 byte and 2 GB");
        }
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("expires", "7d");
        fields.put("maxDownloads", "1");
        fields.put("autoDelete", "true");
        String response = MultipartFileClient.post(endpoint, fields, "file", file, contentType);
        JsonObject json = parse(response);
        JsonElement success = json.get("success");
        if (success == null || !success.getAsBoolean()) {
            throw new IOException("file.io rejected the report");
        }
        JsonElement linkElement = json.get("link");
        if (linkElement == null) {
            throw new IOException("file.io response has no link");
        }
        URI link = URI.create(linkElement.getAsString());
        if (!"https".equals(link.getScheme()) || !"file.io".equals(link.getHost())) {
            throw new IOException("Invalid file.io link");
        }
        JsonElement key = json.get("key");
        return UploadReceipt.permanent(link, key == null ? "" : key.getAsString(), getBackendId());
    }

    private static JsonObject parse(String response) throws IOException {
        try {
            return JsonParser.parseString(response).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid file.io response", exception);
        }
    }
}
