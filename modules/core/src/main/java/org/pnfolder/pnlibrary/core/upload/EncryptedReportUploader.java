package org.pnfolder.pnlibrary.core.upload;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/** Uploads an encrypted envelope directly to a custom HTTPS storage endpoint. */
public final class EncryptedReportUploader implements UploadProvider {
    private static final Gson JSON = new Gson();
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{8,128}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{24,256}");
    private static final long MAX_UPLOAD_BYTES = 64L * 1024 * 1024;

    private final URI endpoint;
    private final URI publicBase;

    public EncryptedReportUploader(@NotNull URI endpoint, @NotNull URI publicBase) {
        this(endpoint, publicBase, true);
    }

    /**
     * @param endpoint storage endpoint that receives reports
     * @param publicBase base of the public report links; its path must end with {@code /}
     * @param requireHttps whether both URLs must use HTTPS; turned off only for tests
     */
    public EncryptedReportUploader(@NotNull URI endpoint, @NotNull URI publicBase, boolean requireHttps) {
        if (requireHttps && !(isHttps(endpoint) && isHttps(publicBase))) {
            throw new IllegalArgumentException("Encrypted upload URLs must use HTTPS");
        }
        String path = publicBase.getPath();
        if (path == null || !path.endsWith("/")) {
            throw new IllegalArgumentException("Encrypted publicBase path must end with /");
        }
        this.endpoint = endpoint;
        this.publicBase = publicBase;
    }

    @Override
    public @NotNull String getBackendId() {
        return "encrypted";
    }

    @Override
    public @NotNull UploadReceipt upload(@NotNull String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = openPost("application/json; charset=UTF-8", body.length);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            return readReceipt(connection);
        } finally {
            connection.disconnect();
        }
    }

    /** Streams the file instead of loading it whole, so a report of up to 64 MB does not take as much heap. */
    @Override
    public @NotNull UploadReceipt uploadFile(@NotNull Path file, @NotNull String contentType) throws IOException {
        long size = Files.size(file);
        if (size > MAX_UPLOAD_BYTES) {
            throw new IOException("Report is too large to upload: " + size + " bytes");
        }
        HttpURLConnection connection = openPost(contentType, size);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                Files.copy(file, output);
            }
            return readReceipt(connection);
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public boolean delete(@NotNull UploadReceipt receipt) throws IOException {
        if (!receipt.canDelete() || !ID.matcher(receipt.getId()).matches()
            || !TOKEN.matcher(receipt.getDeleteToken()).matches()) {
            return false;
        }
        URI deleteUri = endpoint.resolve(ensureSlash(endpoint.getPath()) + receipt.getId());
        HttpURLConnection connection = open(deleteUri, "DELETE");
        connection.setRequestProperty("Authorization", "Bearer " + receipt.getDeleteToken());
        try {
            int status = connection.getResponseCode();
            return status == 200 || status == 204 || status == 404;
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openPost(String contentType, long length) throws IOException {
        HttpURLConnection connection = open(endpoint, "POST");
        connection.setRequestProperty("Content-Type", contentType);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(length);
        return connection;
    }

    private UploadReceipt readReceipt(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        if (status != 200 && status != 201) {
            throw new IOException("Encrypted storage HTTP " + status);
        }
        byte[] response;
        try (InputStream input = connection.getInputStream()) {
            response = UploadHttp.readBounded(input);
        }
        return parseReceipt(new String(response, StandardCharsets.UTF_8));
    }

    private UploadReceipt parseReceipt(String body) throws IOException {
        try {
            JsonObject value = JSON.fromJson(body, JsonObject.class);
            if (value == null) {
                throw new IOException("Empty storage response");
            }
            if (!value.has("success") || !value.get("success").getAsBoolean()) {
                throw new IOException("Storage rejected report");
            }
            String id = value.get("id").getAsString();
            String token = value.get("deleteToken").getAsString();
            if (!ID.matcher(id).matches() || !TOKEN.matcher(token).matches()) {
                throw new IllegalArgumentException("Invalid storage receipt tokens");
            }
            long created = value.has("created") ? value.get("created").getAsLong() : 0L;
            long expires = value.has("expires") ? value.get("expires").getAsLong() : 0L;
            return new UploadReceipt(publicBase.resolve(id), id, token, created, expires, "encrypted");
        } catch (Exception e) {
            throw new IOException("Invalid storage response", e);
        }
    }

    private static HttpURLConnection open(URI uri, String method) throws IOException {
        HttpURLConnection connection = UploadHttp.open(uri, method, 5_000, 15_000);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static String ensureSlash(String path) {
        if (path == null) {
            return "";
        }
        return path.endsWith("/") ? path : path + "/";
    }

    private static boolean isHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme());
    }
}
