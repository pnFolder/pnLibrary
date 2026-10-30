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
        assertTrue(text.contains("privacy: true"))
        assertTrue(text.contains("# Не включать в отчёт личные данные"))
        assertTrue(text.contains("database.password"))
        assertFalse(text.contains("upload"))
        assertFalse(text.contains("log-records"))
        assertEquals(true, defaults.upload)
        assertEquals(200, defaults.logRecords)

        Files.writeString(file, """
            history-retention-days: 60
            privacy: false
        """.trimIndent())

        val configured = PnLibraryConfigLoader.load(temporary)
        assertEquals(60, configured.historyRetentionDays)
        assertEquals(false, configured.privacy)
        assertTrue(Files.readString(file).contains("# Не включать в отчёт личные данные"))
    }

    @Test
    fun `removes unknown keys after a backup and reports them`() {
        val file = temporary.resolve("config.yml")
        Files.writeString(file, "privcy: false\nprivacy: false\n")
        val warnings = mutableListOf<String>()

        val config = PnLibraryConfigLoader.load(temporary, warnings::add)

        assertFalse(config.privacy)
        assertTrue(warnings.any { it.contains("privcy") })
        assertFalse(Files.readString(file).contains("privcy"))
        assertTrue(Files.list(temporary).use { files -> files.anyMatch { it.fileName.toString().endsWith(".bak") } })
    }

    @Test
    fun `rejects values outside the documented range`() {
        Files.writeString(temporary.resolve("config.yml"), "history-retention-days: 7\n")

        val error = assertThrows(IllegalArgumentException::class.java) { PnLibraryConfigLoader.load(temporary) }

        assertTrue(error.message.orEmpty().contains("history-retention-days"))
    }
}
