package org.pnfolder.pnlibrary.core.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.downloads.DownloadDestination
import org.pnfolder.pnlibrary.core.downloads.settings.downloads
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths
import java.nio.file.Files
import java.nio.file.Path

class LegacySettingsImportTest {
    @TempDir lateinit var directory: Path

    private val warnings = mutableListOf<String>()
    private val paths by lazy { LibraryPaths(directory.resolve("plugins/pnLibrary")) }

    private fun write(file: Path, text: String) {
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    private fun import(): SettingsStore = SettingsStore(paths.settings) { warnings += it }.also { LegacySettingsImport(paths) { text -> warnings += text }.run(it) }

    @Test
    fun `the values of the old files are taken over once and the files are renamed`() {
        write(paths.legacyConfig, "# header\nprivacy: true\nhistory-retention-days: 60\ntime-zone: Europe/Moscow\nno-permission-message: Нельзя\njournal-foreign-plugins: false\n")
        write(paths.legacyDownloadsConfig, "enabled: true\nautomatic: false\ndestinations:\n  data-folder: true\n  cache: false\n")

        val store = import()

        val settings = store.current()
        assertTrue(settings.privacy)
        assertEquals(60, settings.historyRetentionDays)
        assertEquals("Europe/Moscow", settings.timeZone)
        assertEquals("Нельзя", settings.noPermissionMessage)
        assertFalse(settings.downloads().automatic)
        assertEquals(setOf(DownloadDestination.DATA_FOLDER), settings.downloads().destinations)
        assertFalse(Files.exists(paths.legacyConfig))
        assertFalse(Files.exists(paths.legacyDownloadsConfig))
        assertTrue(Files.exists(paths.legacyConfig.resolveSibling("config.yml.old")))
        assertTrue(Files.exists(paths.legacyDownloadsConfig.resolveSibling("downloads.yml.old")))
        assertEquals(setOf("privacy", "history-retention-days", "time-zone", "no-permission-message", "downloads.automatic", "downloads.destinations.cache"),
            store.values().filter { it.changed }.map { it.spec.key }.toSet())

        val again = SettingsStore(paths.settings)
        LegacySettingsImport(paths).run(again)
        assertEquals(settings, again.current(), "a second start changes nothing")
    }

    @Test
    fun `the download choice made in game beats the old file and is removed`() {
        write(paths.legacyDownloadsConfig, "automatic: true\n")
        write(paths.downloads.resolve("preferences.state"), "automatic=false\n")

        val store = import()

        assertFalse(store.current().downloadsAutomatic)
        assertFalse(Files.exists(paths.downloads.resolve("preferences.state")))
    }

    @Test
    fun `what was already set by command is not overwritten by an old file`() {
        SettingsStore(paths.settings).set("history-retention-days", "120")
        write(paths.legacyConfig, "history-retention-days: 45\nprivacy: true\n")

        val store = import()

        assertEquals(120, store.current().historyRetentionDays)
        assertTrue(store.current().privacy)
    }

    @Test
    fun `an invalid or removed key is reported and the rest is still imported`() {
        write(paths.legacyConfig, "history-retention-days: 7\nexcluded-paths:\n  - plugins\nprivacy: true\n")

        val store = import()

        assertEquals(30, store.current().historyRetentionDays)
        assertTrue(store.current().privacy)
        assertTrue(warnings.any { it.contains("history-retention-days") })
        assertTrue(warnings.any { it.contains("excluded-paths") })
    }

    @Test
    fun `a file that is not yaml stays where it is`() {
        write(paths.legacyConfig, "privacy: [unclosed\n")

        val store = import()

        assertEquals(LibrarySettings(), store.current())
        assertTrue(Files.exists(paths.legacyConfig))
        assertTrue(warnings.any { it.contains("config.yml") })
    }

    @Test
    fun `without old files nothing happens`() {
        val store = import()

        assertEquals(LibrarySettings(), store.current())
        assertFalse(Files.exists(paths.settings))
        assertTrue(warnings.isEmpty())
    }
}
