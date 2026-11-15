package org.pnfolder.pnlibrary.core.upload

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** Общий HTTP-слой загрузчиков: одни и те же таймауты, правила и ограничение ответа. */
internal object UploadHttp {
    const val USER_AGENT = "pnLibrary-Diagnostics"

    /** Самый большой ответ, который читается; больше этого хостинг нам не присылает. */
    const val MAX_RESPONSE_BYTES = 16_384

    private const val READ_BUFFER_BYTES = 4096

    fun requireHttps(uri: URI, what: String) {
        require(uri.scheme.equals("https", ignoreCase = true)) { "$what must use HTTPS" }
    }

    /** Открывает соединение без перехода по редиректам: ответ чужого хостинга не должен вести нас дальше. */
    fun open(uri: URI, method: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpURLConnection {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }

    /** Читает ответ, но не больше [limit] байт: иначе чужой сервер мог бы забить память. */
    @Throws(IOException::class)
    fun readBounded(input: InputStream, limit: Int = MAX_RESPONSE_BYTES, what: String = "Response"): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            result.write(buffer, 0, count)
            if (result.size() > limit) throw IOException("$what exceeds limit of $limit bytes")
        }
        return result.toByteArray()
    }
}
