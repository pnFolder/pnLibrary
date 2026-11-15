package org.pnfolder.pnlibrary.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.config.ConfigApply
import org.pnfolder.pnlibrary.api.config.ConfigChange
import org.pnfolder.pnlibrary.api.config.ConfigChangeCause
import org.pnfolder.pnlibrary.api.config.ConfigEditException
import org.pnfolder.pnlibrary.api.config.ConfigEditable
import org.pnfolder.pnlibrary.api.config.ConfigRange
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigSecret
import org.pnfolder.pnlibrary.api.config.ConfigSettingKind
import org.pnfolder.pnlibrary.api.config.ManagedConfig
import org.pnfolder.pnlibrary.api.config.ReloadRequest
import org.pnfolder.pnlibrary.api.config.yaml
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

enum class Difficulty { EASY, HARD }

class ShopSection {
    @field:ConfigEditable("Включён", "Работает ли магазин.")
    var enabled: Boolean = true

    @field:ConfigEditable("Налог", "Доля от цены.")
    @field:ConfigRange(min = 0.0, max = 1.0)
    var tax: Double = 0.1
}

class EditableFile {
    @field:ConfigEditable("Приветствие", "Текст при входе.")
    var greeting: String = "Привет"

    @field:ConfigEditable("Пул", "Сколько соединений.", apply = ConfigApply.RESTART)
    @field:ConfigRange(min = 1.0, max = 64.0)
    var poolSize: Int = 10

    @field:ConfigEditable("Сложность", apply = ConfigApply.RELOAD)
    var difficulty: Difficulty = Difficulty.EASY

    @field:ConfigEditable("Секрет", "Не должен показываться.")
    @field:ConfigSecret
    var token: String = "s3cret"

    var hidden: String = "not marked"

    var shop: ShopSection = ShopSection()
}

class ConfigEditingTest {
    @TempDir lateinit var server: Path

    private fun open(service: ConfigurationServiceImpl): ManagedConfig<EditableFile> {
        val folder = server.resolve("plugins/quests").also { Files.createDirectories(it) }
        val scope: ConfigScope = service.scope(FakeConfigOwner(folder.toFile()), PluginId.of("quests-main"), PluginId.of("quests"), ModuleId.of("main"))
        return scope.yaml<EditableFile>("settings.yml") { EditableFile() }.also { it.load() }
    }

    @Test
    fun `only marked fields are offered and secrets never`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val config = open(service)

            val paths = config.settings.map { it.path }.toSet()

            assertEquals(setOf("greeting", "poolSize", "difficulty", "shop.enabled", "shop.tax"), paths)
            assertFalse("token" in paths)
            assertFalse("hidden" in paths)
            val pool = config.settings.single { it.path == "poolSize" }
            assertEquals(ConfigSettingKind.INTEGER, pool.kind)
            assertEquals(1.0, pool.min)
            assertEquals(64.0, pool.max)
            assertEquals("10", pool.value)
            assertEquals(listOf("EASY", "HARD"), config.settings.single { it.path == "difficulty" }.choices)
        }
    }

    @Test
    fun `an edit changes the value in memory and in the file`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val config = open(service)

            val result = config.edit("greeting", "Здравствуй")
            config.edit("shop.enabled", "off")
            config.edit("difficulty", "hard")

            assertEquals("Привет", result.previous)
            assertEquals("Здравствуй", result.setting.value)
            assertEquals("Здравствуй", config.get().greeting)
            assertFalse(config.get().shop.enabled)
            assertEquals(Difficulty.HARD, config.get().difficulty)
            val text = Files.readString(server.resolve("plugins/quests/settings.yml"))
            assertTrue(text.contains("Здравствуй"), text)
            assertTrue(text.contains("HARD"), text)
        }
    }

    @Test
    fun `a wrong value is refused and nothing changes`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val config = open(service)
            val before = Files.readString(server.resolve("plugins/quests/settings.yml"))

            assertThrows(ConfigEditException::class.java) { config.edit("poolSize", "many") }
            assertThrows(ConfigEditException::class.java) { config.edit("poolSize", "1000") }
            assertThrows(ConfigEditException::class.java) { config.edit("shop.tax", "5") }
            assertThrows(ConfigEditException::class.java) { config.edit("difficulty", "medium") }
            assertThrows(ConfigEditException::class.java) { config.edit("token", "x") }
            assertThrows(ConfigEditException::class.java) { config.edit("hidden", "x") }

            assertEquals(10, config.get().poolSize)
            assertEquals(0.1, config.get().shop.tax)
            assertEquals("s3cret", config.get().token)
            assertEquals(before, Files.readString(server.resolve("plugins/quests/settings.yml")))
        }
    }

    @Test
    fun `listeners hear about reads, edits and settings that need a reload`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val config = open(service)
            val changes = mutableListOf<ConfigChange>()
            val requests = mutableListOf<ReloadRequest>()
            val second = mutableListOf<ReloadRequest>()
            config.onChanged(Consumer { changes += it })
            config.onReloadRequired(Consumer { requests += it }, Consumer { second += it })

            config.edit("greeting", "Hi")
            assertTrue(requests.isEmpty(), "a live setting asks for nothing")
            config.edit("poolSize", "20")
            config.edit("difficulty", "HARD")

            assertEquals(listOf(ConfigApply.RESTART, ConfigApply.RELOAD), requests.map { it.apply })
            assertEquals(2, second.size, "every listener is called")
            assertEquals(ConfigApply.RESTART, service.get(config.id!!)!!.info!!.pendingApply)
            assertEquals(listOf("greeting", "poolSize", "difficulty"), changes.map { it.paths.single() })
            assertTrue(changes.all { it.cause == ConfigChangeCause.EDITED })

            config.reload()
            assertEquals(ConfigChangeCause.RELOADED, changes.last().cause)
            assertEquals(ConfigApply.RESTART, config.info!!.pendingApply, "a reload does not make a restart")
        }
    }

    @Test
    fun `a pending reload is cleared by reading the file again and a failing listener does not stop others`() {
        ConfigurationServiceImpl(FakeOwnerPlatform).use { service ->
            val config = open(service)
            val heard = mutableListOf<ReloadRequest>()
            val subscription = config.onReloadRequired(Consumer { error("broken listener") }, Consumer { heard += it })

            config.edit("difficulty", "HARD")
            assertEquals(1, heard.size)
            assertEquals(ConfigApply.RELOAD, config.info!!.pendingApply)

            config.reload()
            assertNull(config.info!!.pendingApply)

            subscription.close()
            config.edit("difficulty", "EASY")
            assertEquals(1, heard.size, "a closed subscription hears nothing")
        }
    }
}
