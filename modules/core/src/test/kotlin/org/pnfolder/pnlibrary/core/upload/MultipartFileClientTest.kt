package org.pnfolder.pnlibrary.core.upload

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class MultipartFileClientTest {
    private val https = URI.create("https://files.example/upload")

    private fun post(
        dir: Path,
        endpoint: URI = https,
        fields: Map<String, String> = emptyMap(),
        fileField: String = "file",
        contentType: String = "application/zip",
    ) = MultipartFileClient.post(endpoint, fields, fileField, Files.write(dir.resolve("r.zip"), byteArrayOf(1)), contentType)

    @Test
    fun `an insecure endpoint is refused before any connection`(@TempDir dir: Path) {
        assertThrows(IllegalArgumentException::class.java) { post(dir, endpoint = URI.create("http://files.example/upload")) }
    }

    @Test
    fun `unsafe field names and header values are refused`(@TempDir dir: Path) {
        assertThrows(IllegalArgumentException::class.java) { post(dir, fileField = "file\"; x=\"") }
        assertThrows(IllegalArgumentException::class.java) { post(dir, fields = mapOf("a b" to "1")) }
        assertThrows(IllegalArgumentException::class.java) { post(dir, contentType = "text/plain\r\nX-Evil: 1") }
        assertThrows(IllegalArgumentException::class.java) { post(dir, contentType = " ") }
    }
}
