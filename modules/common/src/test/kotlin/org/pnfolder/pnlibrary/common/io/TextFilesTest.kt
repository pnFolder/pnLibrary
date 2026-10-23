package org.pnfolder.pnlibrary.common.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.MalformedInputException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class TextFilesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `round trips Unicode and honors append options`() {
        val file = directory.resolve("text.txt")
        assertEquals(file, TextFiles.write(file, "Привет 🌍"))
        TextFiles.write(file, "!", Charsets.UTF_8, StandardOpenOption.APPEND)
        assertEquals("Привет 🌍!", TextFiles.read(file))
    }

    @Test
    fun `invalid text is rejected before an existing file is replaced`() {
        val file = directory.resolve("text.txt")
        TextFiles.write(file, "original")
        assertThrows(MalformedInputException::class.java) { TextFiles.write(file, "\uD800") }
        assertEquals("original", TextFiles.read(file))
    }

    @Test
    fun `malformed UTF8 is rejected when reading`() {
        val file = directory.resolve("text.txt")
        Files.write(file, byteArrayOf(0xc3.toByte(), 0x28))
        assertThrows(MalformedInputException::class.java) { TextFiles.read(file) }
    }
}
