package org.pnfolder.pnlibrary.updates.engine.net;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;

/**
 * Bounded HTTPS client with explicit redirect and timeout handling.
 *
 * <p>The client never changes JVM-wide networking properties. Hosts that publish IPv6 records
 * without routing IPv6 should start the server with {@code -Djava.net.preferIPv4Stack=true};
 * the JVM reads that flag only at startup, so a library cannot set it reliably anyway.
 */
public class TrustedHttpClient {
    private static final int MAX_REDIRECTS = 5;

    private final Duration connectTimeout;
    private final Duration readTimeout;

    public TrustedHttpClient(@NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /** Checks that {@code uri} is an allowed download address and returns it. */
    @NotNull
    public URI validate(@NotNull URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Update URL must use HTTPS: " + uri);
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Update URL contains forbidden components");
        }
        return uri;
    }

    /** Downloads at most {@code maximumBytes} from validated HTTPS {@code uri}. */
    @NotNull
    public byte[] get(@NotNull URI uri, int maximumBytes) throws IOException {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        return stream(uri, (long) maximumBytes, input -> readBounded(input, maximumBytes));
    }

    /**
     * Opens validated HTTPS {@code uri}, follows redirects and hands the body to {@code reader} without keeping it in memory.
     * The response is refused up front when it announces more than {@code maximumBytes}; {@code reader} must enforce the
     * limit for a body that does not announce its size.
     */
    public <T> T stream(@NotNull URI uri, long maximumBytes, @NotNull StreamReader<T> reader) throws IOException {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        URI current = validate(uri);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = open(current);
            try {
                int status = connection.getResponseCode();
                if (isRedirect(status)) {
                    current = redirectTarget(connection, current, redirect);
                    continue;
                }
                return readBody(connection, status, maximumBytes, reader);
            } finally {
                connection.disconnect();
            }
        }
        throw new IllegalStateException("Unreachable redirect state");
    }

    /** Reads the stream, failing if it exceeds {@code maximumBytes}. */
    @NotNull
    public byte[] readBounded(@NotNull InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        while (true) {
            int read = input.read(buffer);
            if (read < 0) {
                return output.toByteArray();
            }
            total += read;
            if (total > maximumBytes) {
                throw new IllegalArgumentException("Update response exceeds " + maximumBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }
    }

    private HttpURLConnection open(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(millis(connectTimeout));
        connection.setReadTimeout(millis(readTimeout));
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/json");
        connection.setRequestProperty("User-Agent", "pnLibrary-Updater");
        return connection;
    }

    private URI redirectTarget(HttpURLConnection connection, URI current, int redirect) {
        if (redirect == MAX_REDIRECTS) {
            throw new IllegalStateException("Too many update redirects");
        }
        String location = connection.getHeaderField("Location");
        if (location == null) {
            throw new IllegalStateException("Redirect has no Location");
        }
        return TrustedHosts.requireRedirect(validate(current.resolve(location)));
    }

    private <T> T readBody(HttpURLConnection connection, int status, long maximumBytes, StreamReader<T> reader)
        throws IOException {
        if (status < 200 || status > 299) {
            throw new IllegalArgumentException("Update request failed with HTTP " + status);
        }
        long announced = connection.getContentLengthLong();
        if (announced >= 0 && announced > maximumBytes) {
            throw new IllegalArgumentException("Update response exceeds " + maximumBytes + " bytes");
        }
        try (InputStream input = connection.getInputStream()) {
            return reader.read(input);
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static int millis(Duration timeout) {
        return (int) Math.min(timeout.toMillis(), (long) Integer.MAX_VALUE);
    }
}
