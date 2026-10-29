package org.pnfolder.pnlibrary.core.updates.engine

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration

/**
 * Bounded HTTPS client with explicit redirect and timeout handling.
 *
 * The client never changes JVM-wide networking properties. Hosts that publish IPv6 records
 * without routing IPv6 should start the server with `-Djava.net.preferIPv4Stack=true`;
 * the JVM reads that flag only at startup, so a library cannot set it reliably anyway.
 */
open class TrustedHttpClient(
    private val connectTimeout: Duration,
    private val readTimeout: Duration,
) {

    internal fun validate(uri: URI): URI {
        require(uri.scheme.equals("https", true)) { "Update URL must use HTTPS: $uri" }
        require(uri.userInfo == null && uri.fragment == null) { "Update URL contains forbidden components" }
        return uri
    }

    /** Downloads at most [maximumBytes] from validated HTTPS [uri]. */
    open fun get(uri: URI, maximumBytes: Int): ByteArray {
        require(maximumBytes > 0) { "maximumBytes must be positive" }
        return stream(uri, maximumBytes.toLong()) { readBounded(it, maximumBytes) }
    }

    /**
     * Opens validated HTTPS [uri], follows redirects and hands the body to [reader] without keeping it in memory.
     * The response is refused up front when it announces more than [maximumBytes]; [reader] must enforce the limit
     * for a body that does not announce its size.
     */
    open fun <T> stream(uri: URI, maximumBytes: Long, reader: (InputStream) -> T): T {
        require(maximumBytes > 0) { "maximumBytes must be positive" }
        var current = validate(uri)
        repeat(MAX_REDIRECTS + 1) { redirect ->
            val connection = (current.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = this@TrustedHttpClient.connectTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                readTimeout = this@TrustedHttpClient.readTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                setRequestProperty("Accept", "application/vnd.github+json, application/json")
                setRequestProperty("User-Agent", "pnLibrary-Updater")
            }
            try {
                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    if (redirect == MAX_REDIRECTS) error("Too many update redirects")
                    val location = connection.getHeaderField("Location") ?: error("Redirect has no Location")
                    current = validate(current.resolve(location))
                    return@repeat
                }
                require(status in 200..299) { "Update request failed with HTTP $status" }
                require(connection.contentLengthLong < 0 || connection.contentLengthLong <= maximumBytes) {
                    "Update response exceeds $maximumBytes bytes"
                }
                connection.inputStream.use { input ->
                    return reader(input)
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Unreachable redirect state")
    }

    internal fun readBounded(input: InputStream, maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maximumBytes, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maximumBytes) { "Update response exceeds $maximumBytes bytes" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    /** Redirect-policy constants. */
    companion object {
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
