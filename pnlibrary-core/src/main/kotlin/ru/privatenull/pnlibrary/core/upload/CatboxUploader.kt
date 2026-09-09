package ru.privatenull.pnlibrary.core.upload

import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** Uploads the encrypted `.pnsupport` file through Catbox's official multipart API. */
class CatboxUploader(
    private val endpoint: URI = URI.create("https://catbox.moe/user/api.php"),
) : UploadProvider {
    override val backendId: String = "catbox"

    override fun upload(payload: String): UploadReceipt {
        val temporary = Files.createTempFile("pnlibrary-report-", ".pnsupport")
        return try {
            Files.writeString(temporary, payload, StandardCharsets.UTF_8)
            uploadFile(temporary, "application/vnd.pnfolder.support")
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override fun uploadFile(file: Path, contentType: String): UploadReceipt {
        require(Files.isRegularFile(file)) { "Diagnostic report is not a regular file: $file" }
        val size = Files.size(file)
        require(size in 1..MAX_FILE_BYTES) { "Catbox report size must be between 1 byte and 200 MB" }

        val link = MultipartFileClient.post(
            endpoint, mapOf("reqtype" to "fileupload"), "fileToUpload", file, contentType,
        )
        val uri = runCatching { URI.create(link) }.getOrElse { throw IOException("Invalid Catbox response", it) }
        if (uri.scheme != "https" || uri.host != "files.catbox.moe" || uri.path.isNullOrBlank()) {
            throw IOException("Catbox rejected the report: ${link.take(256)}")
        }
        return UploadReceipt(uri, uri.path.substringAfterLast('/'), "", Instant.now().epochSecond, 0, backendId)
    }

    private companion object {
        const val MAX_FILE_BYTES = 200L * 1024L * 1024L
    }
}
