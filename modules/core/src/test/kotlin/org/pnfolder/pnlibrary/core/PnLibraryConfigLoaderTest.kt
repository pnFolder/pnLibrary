package org.pnfolder.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.core.runtime.PnLibraryConfigLoader
import java.nio.file.Files
import java.nio.file.Path

class PnLibraryConfigLoaderTest {
    @TempDir lateinit var temporary: Path

    @Test
    fun `creates a commented file with defaults and loads user values`() {
        val defaults = PnLibraryConfigLoader.load(temporary)
        val file = temporary.resolve("config.yml")
        assertTrue(Files.isRegularFile(file))
        assertEquals("encrypted", defaults.uploadMode)
        assertEquals(true, defaults.privacy)
        val text = Files.readString(file)
        assertTrue(text.contains("upload: true"))
        assertTrue(text.contains("# Отправлять ли готовый отчёт"))
        assertTrue(text.contains("log-records: 200"))
        assertFalse(text.contains("upload-mode"))

        Files.writeString(file, """
            upload: false
            logs: true
            log-records: 42
            cooldown-seconds: 5
            keep-reports: 3
            max-report-bytes: 65536
            delete-after-days: 0
            configs: false
            privacy: false
        """.trimIndent())

        val configured = PnLibraryConfigLoader.load(temporary)
        assertEquals(false, configured.upload)
        assertEquals(42, configured.logRecords)
        assertEquals(3, configured.keepReports)
        assertEquals(false, configured.privacy)
        assertTrue(Files.readString(file).contains("# Прикладывать конфигурационные файлы плагинов"))
    }

    @Test
    fun `removes unknown keys after a backup and reports them`() {
        val file = temporary.resolve("config.yml")
        Files.writeString(file, "uplod: false\nupload: false\n")
        val warnings = mutableListOf<String>()

        val config = PnLibraryConfigLoader.load(temporary, warnings::add)

        assertFalse(config.upload)
        assertTrue(warnings.any { it.contains("uplod") })
        assertFalse(Files.readString(file).contains("uplod"))
        assertTrue(Files.list(temporary).use { files -> files.anyMatch { it.fileName.toString().endsWith(".bak") } })
    }

    @Test
    fun `rejects values outside the documented range`() {
        Files.writeString(temporary.resolve("config.yml"), "log-records: 99999\n")

        val error = assertThrows(IllegalArgumentException::class.java) { PnLibraryConfigLoader.load(temporary) }

        assertTrue(error.message.orEmpty().contains("log-records"))
    }
}
