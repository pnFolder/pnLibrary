package org.pnfolder.pnlibrary.core.upload

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Shared bounded multipart transport used by file-hosting providers.
 *
 * The transport accepts HTTPS endpoints only, rejects control characters in MIME
 * header values, streams file bytes without loading them into memory, bounds the
 * response to 16 KiB, and always disconnects the underlying connection.
 */
internal object MultipartFileClient {
    /**
     * Sends one file and a set of UTF-8 text fields as `multipart/form-data`.
     *
     * @throws IllegalArgumentException for an insecure endpoint or unsafe header value
     * @throws IOException for file, network, HTTP, or response-size failures
     */
    fun post(
        endpoint: URI,
        fields: Map<String, String>,
        fileField: String,
        file: Path,
        contentType: String,
    ): String {
        require(endpoint.scheme.equals("https", ignoreCase = true)) {
            "Multipart endpoint must use HTTPS"
        }
        require(FIELD_NAME.matches(fileField)) { "Invalid multipart file field name" }
        require(isSafeHeaderValue(contentType)) { "Invalid multipart content type" }
        require(fields.keys.all(FIELD_NAME::matches)) { "Invalid multipart field name" }
        val boundary = "----pnLibrary-${UUID.randomUUID()}"
        val prefix = prefix(boundary, fields, fileField, file, contentType)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
        val connection = open(endpoint, boundary, prefix.size().toLong() + Files.size(file) + suffix.size)
        try {
            connection.outputStream.use { output ->
                prefix.writeTo(output)
                Files.newInputStream(file).use { it.copyTo(output) }
                output.write(suffix)
            }
            if (connection.responseCode !in 200..299) throw IOException("${endpoint.host} HTTP ${connection.responseCode}")
            return connection.inputStream.use { readBounded(it, endpoint) }.trim()
        } finally {
            connection.disconnect()
        }
    }

    /** The text fields and the headers of the file part, everything before the file bytes. */
    private fun prefix(boundary: String, fields: Map<String, String>, fileField: String, file: Path, contentType: String): ByteArrayOutputStream {
        val prefix = ByteArrayOutputStream()
        fields.forEach { (name, value) ->
            prefix.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
        }
        val safeName = file.fileName.toString().replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileHeaders = buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$safeName\"\r\n")
            append("Content-Type: $contentType\r\n\r\n")
        }
        prefix.write(fileHeaders.toByteArray())
        return prefix
    }

    private fun open(endpoint: URI, boundary: String, length: Long): HttpURLConnection {
        val connection = endpoint.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("User-Agent", "pnLibrary-Diagnostics")
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(length)
        return connection
    }

    private fun readBounded(input: InputStream, endpoint: URI): String {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            result.write(buffer, 0, count)
            if (result.size() > 16_384) throw IOException("${endpoint.host} response is too large")
        }
        return result.toString(StandardCharsets.UTF_8.name())
    }

    private fun isSafeHeaderValue(value: String): Boolean =
        value.isNotBlank() && value.none { it == '\r' || it == '\n' || it.code < 0x20 }

    private val FIELD_NAME = Regex("[A-Za-z0-9_-]{1,64}")
}
