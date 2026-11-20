package org.pnfolder.pnlibrary.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.config.ConfigRoot
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.nio.file.Path
import java.nio.file.Paths

class ConfigPathsTest {
    @TempDir
    lateinit var root: Path

    private fun paths(): ConfigPaths {
        val folder = root.resolve("plugins").resolve("shop")
        java.nio.file.Files.createDirectories(folder)
        return ConfigPaths(folder, PluginId.of("shop"))
    }

    @Test
    fun `a path inside the plugin folder is accepted`() {
        val target = paths().checked("data/prices.yml", Paths.get("data/prices.yml"), ConfigRoot.PLUGIN)

        assertEquals(root.resolve("plugins").resolve("shop").resolve("data").resolve("prices.yml"), target)
    }

    @Test
    fun `a path that climbs out of the folder is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            paths().checked("../other/x.yml", Paths.get("../other/x.yml"), ConfigRoot.PLUGIN)
        }
    }

    @Test
    fun `only yaml files are accepted`() {
        assertThrows(IllegalArgumentException::class.java) {
            paths().checked("prices.txt", Paths.get("prices.txt"), ConfigRoot.PLUGIN)
        }
    }

    @Test
    fun `the plugins folder is the parent of the plugin folder`() {
        assertEquals(root.resolve("plugins"), paths().rootDirectory(ConfigRoot.PLUGINS))
    }
}
