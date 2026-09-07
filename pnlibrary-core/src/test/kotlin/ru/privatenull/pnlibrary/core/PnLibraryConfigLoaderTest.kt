package ru.privatenull.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.core.runtime.PnLibraryConfigLoader
import java.nio.file.Files
import java.nio.file.Path

class PnLibraryConfigLoaderTest {
    @TempDir lateinit var temporary: Path

    @Test
    fun `creates defaults and loads user values`() {
        val defaults = PnLibraryConfigLoader.load(temporary)
        assertTrue(Files.isRegularFile(temporary.resolve("config.yml")))
        assertEquals("encrypted-mclogs", defaults.uploadMode)

        Files.writeString(temporary.resolve("config.yml"), """
            upload: false
            upload-mode: disabled
            logs: true
            log-records: 42
            cooldown-seconds: 5
            keep-reports: 3
            max-report-bytes: 65536
            delete-after-days: 0
            configs: false
        """.trimIndent())

        val configured = PnLibraryConfigLoader.load(temporary)
        assertEquals(false, configured.upload)
        assertEquals(42, configured.logRecords)
        assertEquals(3, configured.keepReports)
    }

    @Test
    fun `rejects unknown keys`() {
        Files.writeString(temporary.resolve("config.yml"), "uplod: false\n")
        val error = assertThrows(IllegalArgumentException::class.java) { PnLibraryConfigLoader.load(temporary) }
        assertTrue(error.message.orEmpty().contains("uplod"))
    }
}
