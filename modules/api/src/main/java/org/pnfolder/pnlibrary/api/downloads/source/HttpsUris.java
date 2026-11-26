package org.pnfolder.pnlibrary.api.downloads.source;

import java.net.URI;

/** Checks that download addresses use HTTPS. */
final class HttpsUris {
    private HttpsUris() {
    }

    /** Parses {@code value} and returns it when it is an HTTPS URI with a host; otherwise fails with {@code message}. */
    static URI require(String value, String message) {
        URI uri = URI.create(value);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return uri;
    }
}
