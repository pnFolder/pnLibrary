package org.pnfolder.pnlibrary.core.upload

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Uploads an encrypted envelope directly to a custom HTTPS storage endpoint.
 */
class EncryptedReportUploader @JvmOverloads constructor(
    private val endpoint: URI,
    private val publicBase: URI,
    requireHttps: Boolean = true,
) : UploadProvider {

    init {
        require(!requireHttps || (isHttps(endpoint) && isHttps(publicBase))) { "Encrypted upload URLs must use HTTPS" }
        val path = publicBase.path
        require(path != null && path.endsWith("/")) { "Encrypted publicBase path must end with /" }
    }

    override val backendId: String get() = "encrypted"

    @Throws(IOException::class)
    override fun upload(payload: String): UploadReceipt {
        val body = payload.toByteArray(StandardCharsets.UTF_8)
        return send(body.size.toLong(), "application/json; charset=UTF-8") { it.write(body) }
    }

    /** Streams the file instead of loading it whole, so a report of up to 64 MB does not take as much heap. */
    override fun uploadFile(file: Path, contentType: String): UploadReceipt {
        val size = Files.size(file)
        if (size > MAX_UPLOAD_BYTES) throw IOException("Report is too large to upload: $size bytes")
        return send(size, contentType) { output -> Files.newInputStream(file).use { it.copyTo(output) } }
    }

    private fun send(length: Long, contentType: String, write: (OutputStream) -> Unit): UploadReceipt {
        val connection = open(endpoint, "POST")
        connection.setRequestProperty("Content-Type", contentType)
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(length)
        try {
            connection.outputStream.use(write)
            val status = connection.responseCode
            if (status != 200 && status != 201) throw IOException("Encrypted storage HTTP $status")
            val response = connection.inputStream.use { UploadHttp.readBounded(it) }
            return parseReceipt(String(response, StandardCharsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    @Throws(IOException::class)
    override fun delete(receipt: UploadReceipt): Boolean {
        if (!receipt.canDelete() || !ID.matches(receipt.id) || !TOKEN.matches(receipt.deleteToken)) return false
        val deleteUri = endpoint.resolve(ensureSlash(endpoint.path) + receipt.id)
        val connection = open(deleteUri, "DELETE")
        connection.setRequestProperty("Authorization", "Bearer ${receipt.deleteToken}")
        return try {
            val status = connection.responseCode
            status == 200 || status == 204 || status == 404
        } finally {
            connection.disconnect()
        }
    }

    private fun parseReceipt(body: String): UploadReceipt {
        try {
            val value = JSON.fromJson(body, JsonObject::class.java)
                ?: throw IOException("Empty storage response")
            if (!value.has("success") || !value.get("success").asBoolean) {
                throw IOException("Storage rejected report")
            }
            val id = value.get("id").asString
            val token = value.get("deleteToken").asString
            require(ID.matches(id) && TOKEN.matches(token)) {
                "Invalid storage receipt tokens"
            }
            val created = if (value.has("created")) value.get("created").asLong else 0L
            val expires = if (value.has("expires")) value.get("expires").asLong else 0L
            return UploadReceipt(publicBase.resolve(id), id, token, created, expires, "encrypted")
        } catch (e: Exception) {
            throw IOException("Invalid storage response", e)
        }
    }

    private fun open(uri: URI, method: String): HttpURLConnection {
        val connection = UploadHttp.open(uri, method, connectTimeoutMs = 5_000, readTimeoutMs = 15_000)
        connection.setRequestProperty("Accept", "application/json")
        return connection
    }

    private fun ensureSlash(path: String?): String =
        if (path == null || path.endsWith("/")) path ?: "" else "$path/"

    private fun isHttps(uri: URI) = "https".equals(uri.scheme, ignoreCase = true)

    /** Shared JSON codec for validated storage receipts. */
    companion object {
        private val JSON = Gson()
        private val ID = Regex("[A-Za-z0-9_-]{8,128}")
        private val TOKEN = Regex("[A-Za-z0-9_-]{24,256}")
        private const val MAX_UPLOAD_BYTES = 64L * 1024 * 1024
    }
}
