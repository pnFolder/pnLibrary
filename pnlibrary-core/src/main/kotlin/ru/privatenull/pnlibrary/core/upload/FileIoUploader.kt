package ru.privatenull.pnlibrary.core.upload

import com.google.gson.JsonParser
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** Temporary fallback: file.io deletes the archive after its first download. */
class FileIoUploader(private val endpoint: URI = URI.create("https://file.io")) : UploadProvider {
    override val backendId = "fileio"

    override fun upload(payload: String): UploadReceipt = throw IOException("file.io requires a binary file")

    override fun uploadFile(file: Path, contentType: String): UploadReceipt {
        require(Files.size(file) in 1..MAX_BYTES) { "file.io report size must be between 1 byte and 2 GB" }
        val response = MultipartFileClient.post(
            endpoint, mapOf("expires" to "7d", "maxDownloads" to "1", "autoDelete" to "true"),
            "file", file, contentType,
        )
        val json = runCatching { JsonParser.parseString(response).asJsonObject }
            .getOrElse { throw IOException("Invalid file.io response", it) }
        if (!json.get("success")?.asBoolean.orFalse()) throw IOException("file.io rejected the report")
        val link = URI.create(json.get("link")?.asString ?: throw IOException("file.io response has no link"))
        if (link.scheme != "https" || link.host != "file.io") throw IOException("Invalid file.io link")
        return UploadReceipt(link, json.get("key")?.asString.orEmpty(), "", Instant.now().epochSecond, 0, backendId)
    }

    private fun Boolean?.orFalse() = this ?: false
    private companion object { const val MAX_BYTES = 2L * 1024 * 1024 * 1024 }
}
