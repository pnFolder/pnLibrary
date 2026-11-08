package org.pnfolder.pnlibrary.core.updates.engine

import java.net.URI

/**
 * Hosts a plugin JAR may be downloaded from. The SHA-256 comes from the same catalog as the link, so
 * a hijacked catalog entry could otherwise point the automatic download at any server.
 */
internal object TrustedHosts {
    private val downloads = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "raw.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "cdn.jsdelivr.net",
    )

    /** Redirects of any update request may additionally land on the GitHub API. */
    private val redirects = downloads + "api.github.com"

    /** Refuses [uri] unless it is HTTPS on a download host. */
    fun requireDownload(uri: URI): URI {
        require(uri.scheme.equals("https", true)) { "Update URL must use HTTPS: $uri" }
        require(uri.host?.lowercase() in downloads) {
            "Скачивание JAR с хоста ${uri.host} запрещено: разрешены ${downloads.joinToString()}"
        }
        return uri
    }

    /** Refuses a redirect that leaves the trusted hosts. */
    fun requireRedirect(uri: URI): URI {
        require(uri.host?.lowercase() in redirects) { "Редирект на недоверенный хост ${uri.host} отклонён: $uri" }
        return uri
    }
}
