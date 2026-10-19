package org.pnfolder.pnlibrary.api.internal

import java.net.URI

/** Returns whether this URI uses the HTTPS scheme and names a non-blank host. */
internal fun URI.isHttps(): Boolean = scheme.equals("https", ignoreCase = true) && !host.isNullOrBlank()

/** Parses [value] and returns it when it is an HTTPS URI with a host; otherwise fails with [message]. */
internal fun requireHttpsUri(value: String, message: String): URI {
    val uri = URI.create(value)
    require(uri.isHttps()) { message }
    return uri
}
