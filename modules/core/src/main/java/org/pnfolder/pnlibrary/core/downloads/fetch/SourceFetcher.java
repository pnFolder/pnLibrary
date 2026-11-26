package org.pnfolder.pnlibrary.core.downloads.fetch;

import java.util.Objects;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.downloads.source.DirectDownloadSource;
import org.pnfolder.pnlibrary.common.io.Digests;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;

/**
 * Gets the bytes of one declared file from its address, then from its mirrors in order.
 *
 * <p>With a SHA-256 declared, the first address whose file matches wins. Without one, the first address that answers
 * wins: the file is taken as it came, and nothing is compared with other addresses or remembered.
 */
public final class SourceFetcher {
    private final TrustedHttpClient http;

    /** Creates a fetcher that reads through {@code http}. */
    public SourceFetcher(@NotNull TrustedHttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    /** Reads the file of {@code source}, at most {@code limit} bytes. */
    public byte[] fetch(@NotNull DirectDownloadSource source, int limit) throws IOException {
        List<URI> addresses = new ArrayList<>();
        addresses.add(source.getUri());
        addresses.addAll(source.getMirrors());
        String expected = source.getSha256();
        List<String> failures = new ArrayList<>();
        boolean answered = false;
        for (URI address : addresses) {
            byte[] bytes;
            try {
                bytes = http.get(address, limit);
            } catch (Exception error) {
                if (addresses.size() == 1) {
                    throw rethrow(error);
                }
                failures.add(address + ": " + error.getMessage());
                continue;
            }
            if (expected == null || Digests.sha256(bytes).equalsIgnoreCase(expected)) {
                return bytes;
            }
            answered = true;
            failures.add(address + ": SHA-256 файла не совпадает");
        }
        String message = "ни один источник не дал нужный файл (" + String.join("; ", failures) + ")";
        throw answered ? new IllegalArgumentException(message) : new IllegalStateException(message);
    }

    private static IOException rethrow(Exception error) throws IOException {
        if (error instanceof IOException) {
            throw (IOException) error;
        }
        throw (RuntimeException) error;
    }
}
