package org.pnfolder.pnlibrary.localization.internal;

import org.pnfolder.pnlibrary.localization.TranslationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * HTTPS-only client for the fixed set of Mojang hosts, with bounded timeouts and no redirects.
 * Not final so tests can replace the transport.
 */
class LocalizationHttpClient {
    /** The only hosts that serve Minecraft metadata and assets. */
    private static final Set<String> ALLOWED_HOSTS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "piston-meta.mojang.com", "launchermeta.mojang.com",
            "resources.download.minecraft.net", "piston-data.mojang.com")));

    private final Duration connectTimeout;
    private final Duration readTimeout;

    LocalizationHttpClient(Duration connectTimeout, Duration readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /** Reads the body of a response and returns what it produced. */
    private interface BodyReader<T> {
        T read(InputStream input) throws IOException;
    }

    /** Downloads {@code uri} into memory; fails with INTEGRITY when it exceeds {@code maximumBytes}. */
    byte[] get(URI uri, final int maximumBytes) throws IOException {
        return request(uri, maximumBytes, new BodyReader<byte[]>() {
            @Override
            public byte[] read(InputStream input) throws IOException {
                ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
                copyBounded(input, output, maximumBytes);
                return output.toByteArray();
            }
        });
    }

    /** Downloads {@code uri} into the file {@code destination} and returns the byte count; fails when it exceeds {@code maximumBytes}. */
    long download(URI uri, final int maximumBytes, final Path destination) throws IOException {
        return request(uri, maximumBytes, new BodyReader<Long>() {
            @Override
            public Long read(InputStream input) throws IOException {
                try (OutputStream output = Files.newOutputStream(
                        destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    return copyBounded(input, output, maximumBytes);
                }
            }
        });
    }

    /** Opens {@code uri}, checks the status and declared size, runs {@code reader} on the body and always disconnects. */
    private <T> T request(URI uri, int maximumBytes, BodyReader<T> reader) throws IOException {
        HttpURLConnection connection = connection(uri);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status > 299) {
                throw new TranslationException(
                        TranslationException.Reason.REMOTE, "Minecraft resource request failed with HTTP " + status);
            }
            if (connection.getContentLengthLong() > maximumBytes) {
                throw tooLarge(maximumBytes);
            }
            try (InputStream input = connection.getInputStream()) {
                return reader.read(input);
            }
        } catch (TranslationException error) {
            throw error;
        } catch (Exception error) {
            throw new TranslationException(
                    TranslationException.Reason.OFFLINE, "Unable to download Minecraft resource", error);
        } finally {
            connection.disconnect();
        }
    }

    /** Copies {@code input} to {@code output} and returns the byte count; stops with INTEGRITY past {@code maximumBytes} whatever the headers said. */
    private static long copyBounded(InputStream input, OutputStream output, int maximumBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        while (true) {
            int read = input.read(buffer);
            if (read < 0) {
                return total;
            }
            total += read;
            if (total > maximumBytes) {
                throw tooLarge(maximumBytes);
            }
            output.write(buffer, 0, read);
        }
    }

    private static TranslationException tooLarge(int maximumBytes) {
        return new TranslationException(
                TranslationException.Reason.INTEGRITY, "Minecraft resource exceeds " + maximumBytes + " bytes");
    }

    /** Opens a connection after checking that the host is on the Mojang allow list. */
    private HttpURLConnection connection(URI uri) throws IOException {
        if (!"https".equals(uri.getScheme()) || !ALLOWED_HOSTS.contains(uri.getHost())) {
            throw new IllegalArgumentException("Unsupported localization host: " + uri);
        }
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(timeoutMillis(connectTimeout));
        connection.setReadTimeout(timeoutMillis(readTimeout));
        connection.setRequestProperty("Accept", "application/json, application/octet-stream");
        return connection;
    }

    private static int timeoutMillis(Duration timeout) {
        return (int) Math.min(timeout.toMillis(), (long) Integer.MAX_VALUE);
    }
}
