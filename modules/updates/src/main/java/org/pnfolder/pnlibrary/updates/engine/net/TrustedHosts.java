package org.pnfolder.pnlibrary.updates.engine.net;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Hosts a plugin JAR may be downloaded from. The SHA-256 comes from the same catalog as the link, so
 * a hijacked catalog entry could otherwise point the automatic download at any server.
 */
public final class TrustedHosts {
    private static final Set<String> DOWNLOADS = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        "github.com",
        "objects.githubusercontent.com",
        "raw.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "cdn.jsdelivr.net")));

    /** Redirects of any update request may additionally land on the GitHub API. */
    private static final Set<String> REDIRECTS = redirects();

    private TrustedHosts() {
    }

    /** Refuses {@code uri} unless it is HTTPS on a download host. */
    @NotNull
    public static URI requireDownload(@NotNull URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Update URL must use HTTPS: " + uri);
        }
        if (!isIn(uri, DOWNLOADS)) {
            throw new IllegalArgumentException("Скачивание JAR с хоста " + uri.getHost() + " запрещено: разрешены "
                + String.join(", ", DOWNLOADS));
        }
        return uri;
    }

    /** Refuses a redirect that leaves the trusted hosts. */
    @NotNull
    public static URI requireRedirect(@NotNull URI uri) {
        if (!isIn(uri, REDIRECTS)) {
            throw new IllegalArgumentException("Редирект на недоверенный хост " + uri.getHost() + " отклонён: " + uri);
        }
        return uri;
    }

    private static boolean isIn(URI uri, Set<String> hosts) {
        return uri.getHost() != null && hosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
    }

    private static Set<String> redirects() {
        Set<String> hosts = new HashSet<String>(DOWNLOADS);
        hosts.add("api.github.com");
        return Collections.unmodifiableSet(hosts);
    }
}
