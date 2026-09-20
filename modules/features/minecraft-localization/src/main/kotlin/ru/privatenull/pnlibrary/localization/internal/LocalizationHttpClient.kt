package ru.privatenull.pnlibrary.localization.internal

import ru.privatenull.pnlibrary.localization.TranslationException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration

internal open class LocalizationHttpClient(
    private val connectTimeout: Duration,
    private val readTimeout: Duration,
) {
    open fun get(uri: URI, maximumBytes: Int): ByteArray {
        val connection = connection(uri)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw TranslationException(
                TranslationException.Reason.REMOTE, "Minecraft resource request failed with HTTP $status",
            )
            val declared = connection.contentLengthLong
            if (declared > maximumBytes) throw TranslationException(
                TranslationException.Reason.INTEGRITY, "Minecraft resource exceeds $maximumBytes bytes",
            )
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(minOf(maximumBytes, 8192))
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maximumBytes) throw TranslationException(
                        TranslationException.Reason.INTEGRITY, "Minecraft resource exceeds $maximumBytes bytes",
                    )
                    output.write(buffer, 0, read)
                }
                return output.toByteArray()
            }
        } catch (error: TranslationException) {
            throw error
        } catch (error: Exception) {
            throw TranslationException(TranslationException.Reason.OFFLINE, "Unable to download Minecraft resource", error)
        } finally {
            connection.disconnect()
        }
    }

    open fun download(uri: URI, maximumBytes: Int, destination: Path): Long {
        val connection = connection(uri)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw TranslationException(
                TranslationException.Reason.REMOTE, "Minecraft resource request failed with HTTP $status",
            )
            val declared = connection.contentLengthLong
            if (declared > maximumBytes) throw TranslationException(
                TranslationException.Reason.INTEGRITY, "Minecraft resource exceeds $maximumBytes bytes",
            )
            connection.inputStream.use { input ->
                Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maximumBytes) throw TranslationException(
                            TranslationException.Reason.INTEGRITY, "Minecraft resource exceeds $maximumBytes bytes",
                        )
                        output.write(buffer, 0, read)
                    }
                    return total
                }
            }
        } catch (error: TranslationException) {
            throw error
        } catch (error: Exception) {
            throw TranslationException(TranslationException.Reason.OFFLINE, "Unable to download Minecraft resource", error)
        } finally {
            connection.disconnect()
        }
    }

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
        private val ALLOWED_HOSTS = setOf(
            "piston-meta.mojang.com", "launchermeta.mojang.com",
            "resources.download.minecraft.net", "piston-data.mojang.com",
        )
    }
}
