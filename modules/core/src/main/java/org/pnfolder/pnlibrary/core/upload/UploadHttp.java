package org.pnfolder.pnlibrary.core.upload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import org.jetbrains.annotations.NotNull;

/** HTTP plumbing shared by the upload providers: timeouts, HTTPS rule and a bounded response read. */
final class UploadHttp {
    static final String USER_AGENT = "pnLibrary-Diagnostics";

    /** Largest response body that is read; the hosting services never send more. */
    static final int MAX_RESPONSE_BYTES = 16_384;

    private static final int READ_BUFFER_BYTES = 4096;

    private UploadHttp() {
    }

    static void requireHttps(@NotNull URI uri, @NotNull String what) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(what + " must use HTTPS");
        }
    }

    /** Opens a connection that never follows redirects, so a foreign host cannot send us elsewhere. */
    static @NotNull HttpURLConnection open(@NotNull URI uri, @NotNull String method, int connectTimeoutMs,
                                           int readTimeoutMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    static @NotNull byte[] readBounded(@NotNull InputStream input) throws IOException {
        return readBounded(input, MAX_RESPONSE_BYTES, "Response");
    }

    /** Reads the response but stops after {@code limit} bytes, so a hostile server cannot fill the memory. */
    static @NotNull byte[] readBounded(@NotNull InputStream input, int limit, @NotNull String what) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[READ_BUFFER_BYTES];
        while (true) {
            int count = input.read(buffer);
            if (count < 0) {
                return result.toByteArray();
            }
            result.write(buffer, 0, count);
            if (result.size() > limit) {
                throw new IOException(what + " exceeds limit of " + limit + " bytes");
            }
        }
    }
}
