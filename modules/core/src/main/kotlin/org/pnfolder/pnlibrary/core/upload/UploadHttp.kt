package org.pnfolder.pnlibrary.core.upload

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** HTTP plumbing shared by the upload providers: timeouts, HTTPS rule and a bounded response read. */
internal object UploadHttp {
    const val USER_AGENT = "pnLibrary-Diagnostics"

    /** Largest response body that is read; the hosting services never send more. */
    const val MAX_RESPONSE_BYTES = 16_384

    private const val READ_BUFFER_BYTES = 4096

    fun requireHttps(uri: URI, what: String) {
        require(uri.scheme.equals("https", ignoreCase = true)) { "$what must use HTTPS" }
    }

    /** Opens a connection that never follows redirects, so a foreign host cannot send us elsewhere. */
    fun open(uri: URI, method: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpURLConnection {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }

    /** Reads the response but stops after [limit] bytes, so a hostile server cannot fill the memory. */
    @Throws(IOException::class)
    fun readBounded(input: InputStream, limit: Int = MAX_RESPONSE_BYTES, what: String = "Response"): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            result.write(buffer, 0, count)
            if (result.size() > limit) throw IOException("$what exceeds limit of $limit bytes")
        }
        return result.toByteArray()
    }
}
