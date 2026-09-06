package ru.privatenull.pnlibrary.core.upload




import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * mclo.gs JSON API uploader.
 *
 * Uses no account, API key, automatic redirects or retries.
 * The production endpoint is always HTTPS; a custom endpoint is injectable
 * for tests only.
 */
class MclogsUploader(
    private val endpoint: URI = DEFAULT_ENDPOINT,
) : ReportUploader {

    override val backendId: String get() = "mclogs"

    @Throws(IOException::class)
    override fun upload(payload: String): UploadReceipt {
        val body = JSON.toJson(mapOf("content" to payload, "source" to "pnLibrary"))
            .toByteArray(StandardCharsets.UTF_8)

        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout    = 10_000
        connection.instanceFollowRedirects = false
        connection.requestMethod  = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "pnLibrary-Diagnostics")
        connection.doOutput       = true
        connection.setFixedLengthStreamingMode(body.size)
        try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status != 200) throw IOException("mclo.gs HTTP $status")
            val response = connection.inputStream.use { it.readLimited(MAX_RESPONSE_BYTES) }
            return parseResponse(String(response, StandardCharsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    @Throws(IOException::class)
    override fun delete(receipt: UploadReceipt): Boolean {
        if (!receipt.canDelete()) return false
        val connection = URI.create("https://api.mclo.gs/1/log/${receipt.id}")
            .toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout    = 10_000
        connection.instanceFollowRedirects = false
        connection.requestMethod  = "DELETE"
        connection.setRequestProperty("Authorization", "Bearer ${receipt.deleteToken}")
        connection.setRequestProperty("Accept", "application/json")
        return try {
            connection.responseCode == 200
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val DEFAULT_ENDPOINT = URI.create("https://api.mclo.gs/1/log")
        private val JSON = Gson()
        private const val MAX_RESPONSE_BYTES = 16_384

        /** Visible for tests. */
        @JvmStatic
        @Throws(IOException::class)
        fun parseResponse(body: String): UploadReceipt {
            try {
                val obj = JSON.fromJson(body, JsonObject::class.java)
                    ?: throw IOException("mclo.gs: null response")
                if (!obj.has("success") || !obj["success"].isJsonPrimitive
                    || !obj["success"].asBoolean)
                    throw IOException("mclo.gs rejected the report")

                val id = obj["id"].asString
                if (!id.matches(Regex("[A-Za-z0-9]{1,64}")))
                    throw IOException("Invalid mclo.gs report id")

                val token = if (obj.has("token") && obj["token"].isJsonPrimitive)
                    obj["token"].asString else ""
                if (token.isNotEmpty() && !token.matches(Regex("[A-Za-z0-9]{16,256}")))
                    throw IOException("Invalid mclo.gs delete token")

                return UploadReceipt(
                    link                = URI.create("https://mclo.gs/$id"),
                    id                  = id,
                    deleteToken         = token,
                    createdEpochSeconds = obj.number("created"),
                    expiresEpochSeconds = obj.number("expires"),
                    backend             = "mclogs",
                )
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException("Invalid mclo.gs response", e)
            }
        }

        private fun JsonObject.number(name: String): Long =
            runCatching { if (has(name)) get(name).asLong else 0L }.getOrDefault(0L)
    }
}

// ── Internal helpers ─────────────────────────────────────────────────────────

private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(4096)
    var n: Int
    while (read(buf, 0, minOf(buf.size, limit + 1 - out.size())).also { n = it } != -1) {
        out.write(buf, 0, n)
        if (out.size() > limit) throw IOException("Response exceeds $limit bytes")
    }
    return out.toByteArray()
}
