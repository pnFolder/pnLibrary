package org.pnfolder.pnlibrary.common.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TsvRecordsTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `records come back with tabs, line breaks and backslashes intact`() {
        val file = directory.resolve("nested").resolve("table.tsv")
        val records = listOf(listOf("a\tb", "line\none", "x\\y"), listOf("", null, "plain"))

        assertTrue(TsvRecords.write(file, "#table 1", records))

        assertEquals(listOf(listOf("a\tb", "line\none", "x\\y"), listOf("", "", "plain")), TsvRecords.read(file))
    }

    @Test
    fun `a missing file reads as no records and the header is not a record`() {
        val file = directory.resolve("table.tsv")
        assertEquals(emptyList<List<String>>(), TsvRecords.read(file))

        TsvRecords.write(file, "#table 1", emptyList())

        assertEquals(emptyList<List<String>>(), TsvRecords.read(file))
        assertEquals("#table 1\n", Files.readString(file))
    }
}
