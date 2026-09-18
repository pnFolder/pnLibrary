package ru.privatenull.pnlibrary.core.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PersistentDiagnosticHistoryTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `disabled encryption never persists plaintext history`() {
        val history = PersistentDiagnosticHistory(
            directory = temporaryDirectory,
            codec = null,
            retentionDays = 30,
            maxBytes = 1_024,
            clock = clock,
        )

        history.save(listOf(mapOf("message" to "secret")), emptyMap())

        assertFalse(Files.list(temporaryDirectory).use { it.findAny().isPresent })
    }

    @Test
    fun `file listing respects aggregate encrypted byte budget`() {
        Files.write(temporaryDirectory.resolve("a.pndlog"), ByteArray(3) { 1 })
        Files.write(temporaryDirectory.resolve("b.pndlog"), ByteArray(3) { 2 })
        Files.write(temporaryDirectory.resolve("ignored.txt"), ByteArray(1))
        val history = PersistentDiagnosticHistory(
            directory = temporaryDirectory,
            codec = null,
            retentionDays = 30,
            maxBytes = 4,
            clock = clock,
        )

        val files = history.files()

        assertEquals(listOf("a.pndlog"), files.map { it.first })
        assertEquals(3, files.single().second.size)
    }

    @Test
    fun `rejects invalid retention limits`() {
        assertThrows(IllegalArgumentException::class.java) {
            PersistentDiagnosticHistory(temporaryDirectory, null, retentionDays = 0, maxBytes = 1, clock = clock)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersistentDiagnosticHistory(temporaryDirectory, null, retentionDays = 1, maxBytes = 0, clock = clock)
        }
    }
}
