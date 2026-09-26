package ru.privatenull.pnlibrary.update

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.io.InputStream

open class TrustedHttpClient(
    private val connectTimeout: Duration,
    private val readTimeout: Duration,
    additionalHosts: Set<String>,
) {
    internal fun validate(uri: URI): URI {
        require(uri.scheme.equals("https", true)) { "update URL must use HTTPS: $uri" }
        require(uri.userInfo == null && uri.fragment == null) { "update URL contains forbidden components" }
        return uri
    }

    open fun get(uri: URI, maximumBytes: Int): ByteArray {
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
                    if (redirect == MAX_REDIRECTS) error("too many update redirects")
                    val location = connection.getHeaderField("Location") ?: error("redirect has no Location")
                    current = validate(current.resolve(location))
                    return@repeat
                }
                require(status in 200..299) { "update request failed with HTTP $status" }
                require(connection.contentLengthLong < 0 || connection.contentLengthLong <= maximumBytes) {
                    "update response exceeds $maximumBytes bytes"
                }
                connection.inputStream.use { input ->
                    return readBounded(input, maximumBytes)
                }
            } finally {
                connection.disconnect()
            }
        }
        error("unreachable redirect state")
    }

    internal fun readBounded(input: InputStream, maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maximumBytes, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maximumBytes) { "update response exceeds $maximumBytes bytes" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
