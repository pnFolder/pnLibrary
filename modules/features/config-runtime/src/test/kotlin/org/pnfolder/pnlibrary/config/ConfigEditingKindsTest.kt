package org.pnfolder.pnlibrary.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.config.ConfigDuration
import org.pnfolder.pnlibrary.api.config.ConfigEditException
import org.pnfolder.pnlibrary.api.config.ConfigEditable
import org.pnfolder.pnlibrary.api.config.ConfigHint
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind
import org.pnfolder.pnlibrary.api.config.ConfigStep
import org.pnfolder.pnlibrary.api.config.ManagedConfig
import org.pnfolder.pnlibrary.api.config.yaml
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class RichFile {
    @field:ConfigEditable("Задержка", "Через сколько срабатывает.")
    @field:ConfigDuration(TimeUnit.SECONDS)
    var delay: Int = 30

    @field:ConfigEditable("Радиус")
    @field:ConfigStep(5.0, "блоков")
    var radius: Int = 10

    @field:ConfigEditable("Режим")
    @field:ConfigHint(values = ["alpha", "beta", "gamma"], strict = true)
    var mode: String = "alpha"

    @field:ConfigEditable("Место")
    @field:ConfigHint(format = "мир;x;y;z", examples = ["world;0;64;0"])
    var place: String = "world;0;64;0"
}

class ConfigEditingKindsTest {
    @TempDir lateinit var server: Path

    private fun open(service: YamlConfigurations): ManagedConfig<RichFile> {
        val folder = server.resolve("plugins/quests").also { Files.createDirectories(it) }
        val scope: ConfigScope = service.scope(FakeConfigOwner(folder.toFile()), PluginId.of("quests-main"), PluginId.of("quests"), ModuleId.of("main"))
        return scope.yaml<RichFile>("rich.yml") { RichFile() }.also { it.load() }
    }

    @Test
    fun `a time is typed with units and kept in the unit of the field`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            val config = open(service)

            config.edit("delay", "1h30m")
            assertEquals(5400, config.get().delay)

            config.edit("delay", "2 мин")
            assertEquals(120, config.get().delay)

            config.edit("delay", "45")
            assertEquals(45, config.get().delay)
        }
    }

    @Test
    fun `the step, the unit and the hints reach the menu`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            val settings = open(service).settings.associateBy { it.path }

            assertEquals(TimeUnit.SECONDS, settings.getValue("delay").duration)
            assertEquals(5.0, settings.getValue("radius").step)
            assertEquals("блоков", settings.getValue("radius").unit)
            assertEquals("мир;x;y;z", settings.getValue("place").format)
            assertEquals(listOf("world;0;64;0"), settings.getValue("place").examples)
        }
    }

    @Test
    fun `a strict list of values makes a text a choice that refuses anything else`() {
        YamlConfigurations(FakeOwnerPlatform).use { service ->
            val config = open(service)
            val mode = config.settings.single { it.path == "mode" }

            assertEquals(ConfigSettingKind.CHOICE, mode.kind)
            assertEquals(listOf("alpha", "beta", "gamma"), mode.choices)

            config.edit("mode", "BETA")
            assertEquals("beta", config.get().mode)
            assertThrows(ConfigEditException::class.java) { config.edit("mode", "delta") }
        }
    }
}
