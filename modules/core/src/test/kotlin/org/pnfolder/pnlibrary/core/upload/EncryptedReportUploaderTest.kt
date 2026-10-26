package org.pnfolder.pnlibrary.core.upload

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class EncryptedReportUploaderTest {
    private lateinit var server: HttpServer
    private val requests = mutableListOf<String>()
    private var status = 200
    private var response = ""

    private val id = "abcdefgh12345"
    private val token = "t".repeat(32)

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += "${exchange.requestMethod} ${exchange.requestURI.path} auth=${exchange.requestHeaders.getFirst("Authorization")}"
            val body = response.toByteArray()
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) exchange.responseBody.use { it.write(body) } else exchange.close()
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun uploader() = EncryptedReportUploader(
        URI.create("http://127.0.0.1:${server.address.port}/store"),
        URI.create("https://reports.example/view/"),
        requireHttps = false,
    )

    private fun receipt(id: String = this.id, token: String = this.token) =
        UploadReceipt(URI.create("https://reports.example/view/$id"), id, token, 0, 0, "encrypted")

    @Test
    fun `a successful upload returns a receipt resolved against the public base`() {
        response = """{"success":true,"id":"$id","deleteToken":"$token","created":10,"expires":20}"""

        val result = uploader().upload("{}")

        assertEquals(URI.create("https://reports.example/view/$id"), result.link)
        assertEquals(id, result.id)
        assertEquals(20L, result.expiresEpochSeconds)
        assertEquals("POST /store auth=null", requests.single())
    }

    @Test
    fun `a rejected, malformed or unsafe storage response is an IOException`() {
        listOf(
            """{"success":false}""",
            "not json",
            """{"success":true,"id":"../evil","deleteToken":"$token"}""",
            """{"success":true,"id":"$id"}""",
        ).forEach { body ->
            response = body
            assertThrows(IOException::class.java) { uploader().upload("{}") }
        }
    }

    @Test
    fun `an HTTP error and an oversized response are IOExceptions`() {
        status = 500
        assertThrows(IOException::class.java) { uploader().upload("{}") }

        status = 200
        response = "x".repeat(20_000)
        assertThrows(IOException::class.java) { uploader().upload("{}") }
    }

    @Test
    fun `deletion sends the bearer token and treats an absent report as deleted`() {
        status = 204
        assertTrue(uploader().delete(receipt()))
        assertEquals("DELETE /store/$id auth=Bearer $token", requests.single())

        status = 404
        assertTrue(uploader().delete(receipt()))

        status = 500
        assertFalse(uploader().delete(receipt()))
    }

    @Test
    fun `deletion refuses ids and tokens that were not issued by the storage`() {
        status = 204

        assertFalse(uploader().delete(receipt(id = "../../admin")))
        assertFalse(uploader().delete(receipt(token = "short")))
        assertFalse(uploader().delete(receipt(token = "")))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a report file is uploaded as its bytes`(@TempDir dir: Path) {
        response = """{"success":true,"id":"$id","deleteToken":"$token"}"""
        val file = Files.write(dir.resolve("report.bin"), byteArrayOf(1, 2, 3))

        assertEquals(id, uploader().uploadFile(file, "application/octet-stream").id)
    }

    @Test
    fun `non-HTTPS endpoints are rejected unless explicitly allowed`() {
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedReportUploader(URI.create("http://host/store"), URI.create("https://host/view/"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedReportUploader(URI.create("https://host/store"), URI.create("https://host/view"))
        }
    }
}
