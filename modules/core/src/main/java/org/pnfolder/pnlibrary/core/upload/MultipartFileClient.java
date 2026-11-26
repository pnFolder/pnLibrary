package org.pnfolder.pnlibrary.core.upload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Shared bounded multipart transport used by file-hosting providers.
 *
 * <p>The transport accepts HTTPS endpoints only, rejects control characters in MIME header values, streams file bytes
 * without loading them into memory, bounds the response to 16 KiB, and always disconnects the underlying connection.
 */
public final class MultipartFileClient {
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern UNSAFE_FILE_NAME_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

    private MultipartFileClient() {
    }

    /**
     * Sends one file and a set of UTF-8 text fields as {@code multipart/form-data}.
     *
     * @throws IllegalArgumentException for an insecure endpoint or unsafe header value
     * @throws IOException for file, network, HTTP, or response-size failures
     */
    public static @NotNull String post(@NotNull URI endpoint, @NotNull Map<String, String> fields,
                                       @NotNull String fileField, @NotNull Path file, @NotNull String contentType)
        throws IOException {
        UploadHttp.requireHttps(endpoint, "Multipart endpoint");
        validate(fields, fileField, contentType);
        String boundary = "----pnLibrary-" + UUID.randomUUID();
        ByteArrayOutputStream prefix = prefix(boundary, fields, fileField, file, contentType);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = open(endpoint, boundary, (long) prefix.size() + Files.size(file) + suffix.length);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                prefix.writeTo(output);
                Files.copy(file, output);
                output.write(suffix);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status > 299) {
                throw new IOException(endpoint.getHost() + " HTTP " + status);
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] body = UploadHttp.readBounded(input, UploadHttp.MAX_RESPONSE_BYTES, endpoint.getHost());
                return new String(body, StandardCharsets.UTF_8).trim();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void validate(Map<String, String> fields, String fileField, String contentType) {
        if (!FIELD_NAME.matcher(fileField).matches()) {
            throw new IllegalArgumentException("Invalid multipart file field name");
        }
        if (!isSafeHeaderValue(contentType)) {
            throw new IllegalArgumentException("Invalid multipart content type");
        }
        for (String name : fields.keySet()) {
            if (!FIELD_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid multipart field name");
            }
        }
    }

    /** The text fields and the headers of the file part, everything before the file bytes. */
    private static ByteArrayOutputStream prefix(String boundary, Map<String, String> fields, String fileField,
                                                Path file, String contentType) throws IOException {
        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            prefix.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        String safeName = UNSAFE_FILE_NAME_CHARS.matcher(file.getFileName().toString()).replaceAll("_");
        String fileHeaders = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + safeName + "\"\r\n"
            + "Content-Type: " + contentType + "\r\n\r\n";
        prefix.write(fileHeaders.getBytes(StandardCharsets.UTF_8));
        return prefix;
    }

    private static HttpURLConnection open(URI endpoint, String boundary, long length) throws IOException {
        HttpURLConnection connection = UploadHttp.open(endpoint, "POST", 10_000, 30_000);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(length);
        return connection;
    }

    private static boolean isSafeHeaderValue(String value) {
        boolean blank = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c < 0x20) {
                return false;
            }
            if (!Character.isWhitespace(c)) {
                blank = false;
            }
        }
        return !blank;
    }
}
