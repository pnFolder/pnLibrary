package org.pnfolder.pnlibrary.core.commands.modules

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class DiskUsageTest {
    @TempDir
    lateinit var plugins: Path

    private fun write(relative: String, size: Int) {
        val file = plugins.resolve(relative)
        Files.createDirectories(file.parent)
        Files.write(file, ByteArray(size))
    }

    @Test
    fun `splits a plugin folder by kind and adds the jar to the same plugin`() {
        write("Shop-1.2.3.jar", 1000)
        write("Shop/config.yml", 10)
        write("Shop/players.db", 200)
        write("Shop/logs/latest.log", 30)
        write("Shop/lib/hikari.jar", 400)
        val report = DiskUsage.scan(plugins, emptyList())
        val row = report.rows.single()
        assertEquals("Shop", row.name)
        assertEquals(DiskRow("Shop", 1000, 10, 400, 30, 200), row)
        assertEquals(1640L, row.total)
    }

    @Test
    fun `a jar without a folder is a plugin of its own and the biggest comes first`() {
        write("Small-1.0.jar", 5)
        write("Big/data.bin", 500)
        val report = DiskUsage.scan(plugins, emptyList())
        assertEquals(listOf("Big", "Small"), report.rows.map { it.name })
        assertEquals(505L, report.total)
    }

    @Test
    fun `registered libraries count once even when listed twice, missing files count zero`() {
        write("lib.jar", 70)
        val file = plugins.resolve("lib.jar").toString()
        val report = DiskUsage.scan(plugins, listOf(file, file, plugins.resolve("none.jar").toString()))
        assertEquals(70L, report.registeredLibraries)
    }

    @Test
    fun `a missing plugins folder gives an empty report`() {
        val report = DiskUsage.scan(plugins.resolve("nowhere"), emptyList())
        assertTrue(report.rows.isEmpty())
        assertEquals(-1L, report.free)
    }

    @Test
    fun `sizes read the same in the command and the menu`() {
        assertEquals("12 Б", DiskUsage.format(12))
        assertEquals("320 КБ", DiskUsage.format(320 * 1024L))
        assertEquals("1.5 МБ", DiskUsage.format(1_572_864))
        assertEquals("2.00 ГБ", DiskUsage.format(2L * 1024 * 1024 * 1024))
        assertEquals("неизвестно", DiskUsage.format(-1))
    }
}
