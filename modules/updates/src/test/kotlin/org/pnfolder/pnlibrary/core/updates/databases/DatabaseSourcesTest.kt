package org.pnfolder.pnlibrary.core.updates.databases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.config.DatabaseKind
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.config.ManagedConfigFile
import org.pnfolder.pnlibrary.config.yaml.DatabasePath
import java.nio.file.Paths

class DatabaseSourcesTest {
    private fun file(vararg names: String) = ManagedConfigFile(
        PluginId.of("pnClans"), Paths.get("plugins/pnClans"), "config.yml", emptyList(),
        names.map { DatabasePath(it.lowercase(), DatabaseKind.MYSQL, it) },
    )

    @Test
    fun `databases are found by plugin without regard to case and forgotten on close`() {
        val sources = DatabaseSources()
        val handle = sources.register(file("Кланы", "Банк"))!!
        assertEquals(listOf("Кланы", "Банк"), sources.of("PNCLANS").map { it.name })
        handle.close()
        assertEquals(0, sources.of("pnclans").size)
    }

    @Test
    fun `two databases with one name are refused`() {
        assertThrows(IllegalArgumentException::class.java) { DatabaseSources().register(file("Кланы", "Кланы")) }
    }
}
