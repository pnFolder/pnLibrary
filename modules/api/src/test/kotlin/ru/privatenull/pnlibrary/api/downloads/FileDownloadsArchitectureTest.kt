package ru.privatenull.pnlibrary.api.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FileDownloadsArchitectureTest {
    @Test
    fun `public downloads API describes files only`() {
        val downloads = Class.forName("ru.privatenull.pnlibrary.api.downloads.FileDownloads")
        val builder = downloads.declaredClasses.single { it.simpleName == "Builder" }

        assertFalse(builder.methods.any { it.name == "component" })
        assertFalse(builder.methods.any { it.name == "plugin" })
        assertEquals(setOf("DATA_FOLDER", "CACHE"), DownloadDestination.entries.map { it.name }.toSet())
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("ru.privatenull.pnlibrary.api.downloads.PluginDownloads")
        }
    }
}
