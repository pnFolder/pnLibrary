package org.pnfolder.pnlibrary.localization.internal

import org.pnfolder.pnlibrary.localization.TranslationException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration

/**
 * HTTPS-only client for the fixed set of Mojang hosts, with bounded timeouts and no redirects.
 * Open so tests can replace the transport.
 */
internal open class LocalizationHttpClient(
    private val connectTimeout: Duration,
    private val readTimeout: Duration,
) {
    /** Downloads [uri] into memory; fails with INTEGRITY when it exceeds [maximumBytes]. */
    open fun get(uri: URI, maximumBytes: Int): ByteArray = request(uri, maximumBytes) { input ->
        val output = ByteArrayOutputStream(minOf(maximumBytes, 8192))
        copyBounded(input, output, maximumBytes)
        output.toByteArray()
    }

    /** Downloads [uri] into the file [destination] and returns the byte count; fails when it exceeds [maximumBytes]. */
    open fun download(uri: URI, maximumBytes: Int, destination: Path): Long = request(uri, maximumBytes) { input ->
        Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
            copyBounded(input, output, maximumBytes)
        }
    }

    /** Opens [uri], checks the status and declared size, runs [read] on the body and always disconnects. */
    private fun <T> request(uri: URI, maximumBytes: Int, read: (InputStream) -> T): T {
        val connection = connection(uri)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw TranslationException(
                TranslationException.Reason.REMOTE, "Minecraft resource request failed with HTTP $status",
            )
            if (connection.contentLengthLong > maximumBytes) throw tooLarge(maximumBytes)
            return connection.inputStream.use(read)
        } catch (error: TranslationException) {
            throw error
        } catch (error: Exception) {
            throw TranslationException(TranslationException.Reason.OFFLINE, "Unable to download Minecraft resource", error)
        } finally {
            connection.disconnect()
        }
    }

    /** Copies [input] to [output] and returns the byte count; stops with INTEGRITY past [maximumBytes] whatever the headers said. */
    private fun copyBounded(input: InputStream, output: OutputStream, maximumBytes: Int): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            if (total > maximumBytes) throw tooLarge(maximumBytes)
            output.write(buffer, 0, read)
        }
    }

    private fun tooLarge(maximumBytes: Int) =
        TranslationException(TranslationException.Reason.INTEGRITY, "Minecraft resource exceeds $maximumBytes bytes")

    /** Opens a connection after checking that the host is on the Mojang allow list. */
    private fun connection(uri: URI): HttpURLConnection {
        require(uri.scheme == "https" && uri.host in ALLOWED_HOSTS) { "Unsupported localization host: $uri" }
        return (uri.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = this@LocalizationHttpClient.connectTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            readTimeout = this@LocalizationHttpClient.readTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            setRequestProperty("Accept", "application/json, application/octet-stream")
        }
    }

    companion object {
        /** The only hosts that serve Minecraft metadata and assets. */
        private val ALLOWED_HOSTS = setOf(
            "piston-meta.mojang.com", "launchermeta.mojang.com",
            "resources.download.minecraft.net", "piston-data.mojang.com",
        )
    }
}
