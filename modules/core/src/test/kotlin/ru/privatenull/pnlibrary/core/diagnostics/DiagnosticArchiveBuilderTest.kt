package ru.privatenull.pnlibrary.core.diagnostics

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class DiagnosticArchiveBuilderTest {
    @Test
    fun `enforces the uncompressed payload budget`() {
        val builder = DiagnosticArchiveBuilder(maximumUncompressedBytes = 4)
        builder.bytes("first.txt", byteArrayOf(1, 2, 3))

        assertThrows(IllegalArgumentException::class.java) {
            builder.bytes("second.txt", byteArrayOf(4, 5))
        }
    }

    @Test
    fun `copies entry bytes and builds without mutating state`() {
        val source = byteArrayOf(1, 2, 3)
        val builder = DiagnosticArchiveBuilder(maximumUncompressedBytes = 10)
        builder.bytes("value.bin", source)
        source[0] = 9

        val first = unzip(builder.build())
        val second = unzip(builder.build())

        assertArrayEquals(byteArrayOf(1, 2, 3), first.getValue("pn-diagnostic/value.bin"))
        assertArrayEquals(first.getValue("pn-diagnostic/value.bin"), second.getValue("pn-diagnostic/value.bin"))
        assertTrue(first.containsKey("pn-diagnostic/checksums.json"))
        assertTrue(second.containsKey("pn-diagnostic/checksums.json"))
    }

    private fun unzip(archive: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readAllBytes()
            }
        }
        return entries
    }
}
