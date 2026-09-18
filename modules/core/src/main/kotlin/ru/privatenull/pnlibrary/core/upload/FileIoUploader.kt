package ru.privatenull.pnlibrary.core.upload

import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Uploads an encrypted report to file.io as a single-download fallback.
 *
 * The provider requests a seven-day expiry and deletion after the first download.
 * It intentionally does not implement remote deletion because file.io supplies no
 * deletion token through this API.
 *
 * @param endpoint HTTPS file.io endpoint
 * @throws IllegalArgumentException if [endpoint] is not HTTPS
 */
class FileIoUploader(private val endpoint: URI = URI.create("https://file.io")) : UploadProvider {
    init {
        require(endpoint.scheme.equals("https", ignoreCase = true)) {
            "file.io endpoint must use HTTPS"
        }
    }

    override val backendId = "fileio"

    override fun upload(payload: String): UploadReceipt = throw IOException("file.io requires a binary file")

    override fun uploadFile(file: Path, contentType: String): UploadReceipt {
        require(Files.size(file) in 1..MAX_BYTES) { "file.io report size must be between 1 byte and 2 GB" }
        val response = MultipartFileClient.post(
            endpoint, mapOf("expires" to "7d", "maxDownloads" to "1", "autoDelete" to "true"),
            "file", file, contentType,
        )
        val json = try {
            JsonParser.parseString(response).asJsonObject
        } catch (exception: RuntimeException) {
            throw IOException("Invalid file.io response", exception)
        }
        if (!json.get("success")?.asBoolean.orFalse()) throw IOException("file.io rejected the report")
        val link = URI.create(json.get("link")?.asString ?: throw IOException("file.io response has no link"))
        if (link.scheme != "https" || link.host != "file.io") throw IOException("Invalid file.io link")
        return UploadReceipt(link, json.get("key")?.asString.orEmpty(), "", Instant.now().epochSecond, 0, backendId)
    }

    private fun Boolean?.orFalse() = this ?: false
    private companion object { const val MAX_BYTES = 2L * 1024 * 1024 * 1024 }
}
