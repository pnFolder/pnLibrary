package org.pnfolder.pnlibrary.core.upload;

import java.io.IOException;
import java.net.URI;
import org.jetbrains.annotations.NotNull;

/** Checks the link a hosting service answered with: HTTPS and the host the service really serves files from. */
final class UploadedLink {
    private UploadedLink() {
    }

    static @NotNull URI parse(@NotNull String service, @NotNull String answer, @NotNull String host) throws IOException {
        if (answer.isEmpty()) {
            throw new IOException(service + " sent an empty answer: the service did not accept the file");
        }
        URI uri;
        try {
            uri = URI.create(answer);
        } catch (IllegalArgumentException exception) {
            throw new IOException(service + " rejected the report: " + shorten(answer), exception);
        }
        String path = uri.getPath();
        if (!"https".equals(uri.getScheme()) || !host.equals(uri.getHost()) || path == null || path.length() < 2) {
            throw new IOException(service + " rejected the report: " + shorten(answer));
        }
        return uri;
    }

    static @NotNull String lastSegment(@NotNull URI uri) {
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String shorten(String text) {
        return text.length() > 256 ? text.substring(0, 256) : text;
    }
}
