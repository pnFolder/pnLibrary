package org.pnfolder.pnlibrary.updates.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LibraryPathsTest {
    @TempDir
    lateinit var directory: Path

    private val outsideData = setOf(
        "pluginFolder", "plugins", "serverUpdateFolder", "legacyConfig", "legacyDownloadsConfig", "localization", "data",
    )

    private fun locations(paths: LibraryPaths): Map<String, Path> =
        LibraryPaths::class.java.methods
            .filter { it.parameterCount == 0 && Path::class.java.isAssignableFrom(it.returnType) && it.name.startsWith("get") }
            .associate { it.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar) to it.invoke(paths) as Path }

    @Test
    fun `every state location lies under the data folder and nothing else does`() {
        val plugin = directory.resolve("plugins/pnLibrary")
        val paths = LibraryPaths(plugin)

        val (outside, inside) = locations(paths).entries.partition { it.key in outsideData }
        assertEquals(outsideData, outside.map { it.key }.toSet())
        assertTrue(inside.isNotEmpty())
        inside.forEach { (name, path) -> assertTrue(path.startsWith(paths.data) && path != paths.data, "$name is $path") }
        assertEquals(plugin.resolve("data"), paths.data)
    }

    @Test
    fun `administrator files and the server update folder stay out of data`() {
        val plugin = directory.resolve("plugins/pnLibrary")
        val paths = LibraryPaths(plugin)

        assertEquals(plugin.resolve("config.yml"), paths.legacyConfig)
        assertEquals(plugin.resolve("downloads.yml"), paths.legacyDownloadsConfig)
        assertEquals(plugin.resolve("localization"), paths.localization)
        assertEquals(directory.resolve("plugins/update"), paths.serverUpdateFolder)
        assertEquals(directory.resolve("plugins"), paths.plugins)
    }

    @Test
    fun `no two state locations are the same`() {
        val inside = locations(LibraryPaths(directory.resolve("plugins/pnLibrary"))).filterKeys { it !in outsideData }
        assertEquals(inside.size, inside.values.toSet().size)
    }
}
