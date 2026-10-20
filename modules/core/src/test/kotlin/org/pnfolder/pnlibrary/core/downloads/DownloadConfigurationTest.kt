package org.pnfolder.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.downloads.DownloadDestination
import java.nio.file.Files
import java.nio.file.Path

class DownloadConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `creates a conservative commented file separate from updates`() {
        val path = directory.resolve("downloads.yml")
        val configuration = DownloadConfiguration.load(path)

        assertTrue(configuration.enabled)
        assertFalse(configuration.automatic)
        val text = Files.readString(path)
        assertTrue(text.contains("enabled: true"))
        assertTrue(text.contains("data-folder: true"))
        assertTrue(text.contains("# Включить систему загрузок"))
    }

    @Test
    fun `reads destination policy without host allowlists`() {
        val path = directory.resolve("downloads.yml")
        Files.writeString(path, """
            enabled: true
            automatic: true
            destinations:
              data-folder: true
              cache: false
        """.trimIndent())

        val configuration = DownloadConfiguration.load(path)

        assertTrue(configuration.automatic)
        assertEquals(setOf(DownloadDestination.DATA_FOLDER), configuration.destinations)
    }

    @Test
    fun `removes stale keys and reports them`() {
        val path = directory.resolve("downloads.yml")
        Files.writeString(path, "downloads:\n  enabled: true\nplugins: false\n")
        val warnings = mutableListOf<String>()

        DownloadConfiguration.load(path, warnings::add)

        assertTrue(warnings.any { it.contains("plugins") })
        assertFalse(Files.readString(path).contains("plugins:"))
    }
}
