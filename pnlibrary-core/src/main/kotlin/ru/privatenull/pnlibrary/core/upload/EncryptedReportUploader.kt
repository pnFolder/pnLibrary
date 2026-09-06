package ru.privatenull.pnlibrary.core.upload




import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * Uploads an encrypted envelope directly to a custom HTTPS storage endpoint.
 */
class EncryptedReportUploader @JvmOverloads constructor(
    private val endpoint: URI,
    private val publicBase: URI,
    requireHttps: Boolean = true,
) : ReportUploader {

    init {
        if (requireHttps && (!"https".equals(endpoint.scheme, ignoreCase = true) || !"https".equals(publicBase.scheme, ignoreCase = true))) {
            throw IllegalArgumentException("Encrypted upload URLs must use HTTPS")
        }
        val path = publicBase.path
        require(path != null && path.endsWith("/")) { "Encrypted publicBase path must end with /" }
    }

    override val backendId: String get() = "encrypted"

    @Throws(IOException::class)
    override fun upload(payload: String): UploadReceipt {
        val body = payload.toByteArray(StandardCharsets.UTF_8)
        val connection = open(endpoint, "POST")
        connection.setRequestProperty("Content-Type", "application/vnd.pnlibrary.encrypted+json; charset=UTF-8")
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(body.size)
        try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status != 200 && status != 201) {
                throw IOException("Encrypted storage HTTP $status")
            }
            val response = connection.inputStream.use { readLimited(it, 16_384) }
            return parseReceipt(String(response, StandardCharsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    @Throws(IOException::class)
    override fun delete(receipt: UploadReceipt): Boolean {
        if (!receipt.canDelete()) return false
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
            require(id.matches(Regex("[A-Za-z0-9_-]{8,128}")) && token.matches(Regex("[A-Za-z0-9_-]{24,256}"))) {
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
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "pnLibrary-Diagnostics")
        return connection
    }

    private fun ensureSlash(path: String?): String =
        if (path == null || path.endsWith("/")) path ?: "" else "$path/"

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var n: Int
        while (input.read(buffer).also { n = it } != -1) {
            out.write(buffer, 0, n)
            if (out.size() > limit) throw IOException("Response exceeds limit of $limit bytes")
        }
        return out.toByteArray()
    }

    companion object {
        private val JSON = Gson()
    }
}
