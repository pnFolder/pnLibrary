package ru.privatenull.pnlibrary.core.diagnostics

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class DiagnosticReportStoreTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `stores the requested report representation`() {
        val store = DiagnosticReportStore(temporaryDirectory)
        val payload = byteArrayOf(1, 2, 3)

        val encrypted = store.save(payload, encrypted = true, keepCount = 2)

        assertTrue(encrypted.fileName.toString().endsWith(".pnsupport"))
        assertArrayEquals(payload, Files.readAllBytes(encrypted))
    }

    @Test
    fun `retention removes only old reports owned by the store`() {
        val store = DiagnosticReportStore(temporaryDirectory)
        val unrelated = Files.writeString(temporaryDirectory.resolve("operator-notes.txt"), "keep")
        val first = store.save(byteArrayOf(1), encrypted = false, keepCount = 2)
        Files.setLastModifiedTime(first, FileTime.fromMillis(1))
        val second = store.save(byteArrayOf(2), encrypted = false, keepCount = 2)
        Files.setLastModifiedTime(second, FileTime.fromMillis(2))

        val newest = store.save(byteArrayOf(3), encrypted = false, keepCount = 2)
        val ownedReports = Files.list(temporaryDirectory).use { stream ->
            stream.filter { it.fileName.toString().startsWith("report-") }.toList()
        }

        assertEquals(2, ownedReports.size)
        assertTrue(Files.exists(second))
        assertTrue(Files.exists(newest))
        assertTrue(Files.exists(unrelated))
    }
}
