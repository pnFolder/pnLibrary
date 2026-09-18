package ru.privatenull.pnlibrary.core.diagnostics

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SecureConfigurationFileReaderTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `reads a regular file within the trusted root`() {
        val expected = "enabled: true\n".toByteArray()
        Files.write(temporaryDirectory.resolve("config.yml"), expected)

        val result = SecureConfigurationFileReader().read(temporaryDirectory, "config.yml")

        assertArrayEquals(expected, result.content)
        assertNull(result.error)
    }

    @Test
    fun `rejects traversal and forbidden extensions`() {
        val reader = SecureConfigurationFileReader()
        Files.write(temporaryDirectory.resolve("cache.db"), byteArrayOf(1))

        val traversal = reader.read(temporaryDirectory, "../outside.yml")
        val forbidden = reader.read(temporaryDirectory, "cache.db")

        assertEquals("[SECURITY: path traversal blocked]", traversal.error)
        assertEquals("[SECURITY: binary or database file extension blocked]", forbidden.error)
    }

    @Test
    fun `rejects files larger than the configured byte budget`() {
        Files.write(temporaryDirectory.resolve("large.yml"), ByteArray(5))

        val result = SecureConfigurationFileReader(maximumBytes = 4)
            .read(temporaryDirectory, "large.yml")

        assertEquals("[file exceeds size limit of 4 bytes (5 bytes)]", result.error)
        assertNull(result.content)
    }
}
