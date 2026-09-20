package ru.privatenull.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import java.nio.file.Files
import java.nio.file.Path

class DownloadConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `creates conservative configuration separate from updates`() {
        val path = directory.resolve("downloads.yml")
        val configuration = DownloadConfiguration.load(path)

        assertTrue(configuration.enabled)
        assertFalse(configuration.automatic)
        assertTrue(Files.readString(path).startsWith("downloads:"))
    }

    @Test
    fun `reads host and destination policy`() {
        val path = directory.resolve("downloads.yml")
        Files.writeString(path, """
            downloads:
              enabled: true
              automatic: true
              allowed-hosts: [cdn.example.org]
              destinations:
                plugins: false
                data-folder: true
                cache: false
        """.trimIndent())

        val configuration = DownloadConfiguration.load(path)

        assertTrue(configuration.automatic)
        assertEquals(setOf("cdn.example.org"), configuration.allowedHosts)
        assertEquals(setOf(DownloadDestination.DATA_FOLDER), configuration.destinations)
    }
}
