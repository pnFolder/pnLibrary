package ru.privatenull.pnlibrary.core.upload

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Uploads the encrypted `.pnsupport` file through Catbox's official multipart API. */
class CatboxUploader(
    private val endpoint: URI = URI.create("https://catbox.moe/user/api.php"),
) : ReportUploader {
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

        val boundary = "----pnLibrary-${UUID.randomUUID()}"
        val crlf = "\r\n"
        val prefix = buildString {
            append("--$boundary$crlf")
            append("Content-Disposition: form-data; name=\"reqtype\"$crlf$crlf")
            append("fileupload$crlf")
            append("--$boundary$crlf")
            append("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"")
            append(file.fileName.toString().replace(Regex("[^A-Za-z0-9._-]"), "_"))
            append("\"$crlf")
            append("Content-Type: $contentType$crlf$crlf")
        }.toByteArray(StandardCharsets.UTF_8)
        val suffix = "$crlf--$boundary--$crlf".toByteArray(StandardCharsets.UTF_8)

        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("Accept", "text/plain")
        connection.setRequestProperty("User-Agent", "pnLibrary-Diagnostics")
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(prefix.size.toLong() + size + suffix.size)
        try {
            connection.outputStream.use { output ->
                output.write(prefix)
                Files.newInputStream(file).use { it.copyTo(output) }
                output.write(suffix)
            }
            if (connection.responseCode != 200) throw IOException("Catbox HTTP ${connection.responseCode}")
            val link = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                input.copyTo(out)
                require(out.size() <= MAX_RESPONSE_BYTES) { "Catbox response is too large" }
                out.toString(StandardCharsets.UTF_8).trim()
            }
            val uri = runCatching { URI.create(link) }.getOrElse { throw IOException("Invalid Catbox response", it) }
            if (uri.scheme != "https" || uri.host != "files.catbox.moe" || uri.path.isNullOrBlank()) {
                throw IOException("Catbox rejected the report: ${link.take(256)}")
            }
            return UploadReceipt(
                link = uri,
                id = uri.path.substringAfterLast('/'),
                deleteToken = "",
                createdEpochSeconds = Instant.now().epochSecond,
                expiresEpochSeconds = 0,
                backend = backendId,
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 200L * 1024L * 1024L
        const val MAX_RESPONSE_BYTES = 4 * 1024
    }
}
