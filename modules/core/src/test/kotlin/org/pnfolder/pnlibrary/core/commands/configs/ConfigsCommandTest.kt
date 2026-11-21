package org.pnfolder.pnlibrary.core.commands.configs

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.config.ConfigApply
import org.pnfolder.pnlibrary.api.config.ConfigEditable
import org.pnfolder.pnlibrary.api.config.ConfigPolymorphic
import org.pnfolder.pnlibrary.api.config.ConfigType
import org.pnfolder.pnlibrary.api.config.ConfigTypes
import org.pnfolder.pnlibrary.api.config.type
import org.pnfolder.pnlibrary.api.config.yaml
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.config.ConfigurationServiceImpl
import org.pnfolder.pnlibrary.core.commands.engine.CommandRoute
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

@ConfigPolymorphic
@ConfigTypes(ConfigType(CoinPrize::class, "coin"))
interface Prize

class CoinPrize : Prize
class GemPrize : Prize
data class PrizeFile(var prizes: List<Prize> = listOf(CoinPrize()))

class TunedFile {
    @field:ConfigEditable("Приветствие", "Текст при входе.")
    var greeting: String = "Привет"

    @field:ConfigEditable("Пул", "Сколько соединений.", apply = ConfigApply.RESTART)
    var pool: Int = 10
}

class ConfigsCommandTest {
    @TempDir lateinit var folder: Path

    private class Owner(val folder: Path)

    private val platform = object : PlatformOwnership {
        override fun ownerDataFolder(owner: Any): Path? = (owner as? Owner)?.folder
        override fun ownerLogger(owner: Any): Logger? = Logger.getLogger("configs-command-test")
    }
    private val service = ConfigurationServiceImpl(platform)
    private val library = mock(PnLibrary::class.java).also { `when`(it.configurations).thenReturn(service) }
    private val definition = ConfigsCommand(library).definition()
    private val sent = mutableListOf<String>()
    private val sender = object : CommandSender {
        override val id = "console"
        override val name = "Console"
        override val isConsole = true
        override fun hasPermission(permission: String) = true
        override fun send(message: Component) {
            sent += PlainTextComponentSerializer.plainText().serialize(message)
        }
    }

    private fun scope(plugin: String) = service.scope(
        Owner(folder.resolve("plugins").resolve(plugin)), PluginId.of("$plugin-main"), PluginId.of(plugin), ModuleId.of("main"),
    )

    private fun run(vararg words: String): String {
        sent.clear()
        val route = CommandTreeRouter().route(definition, CommandContext(sender, words.toList(), "configs", words.joinToString(" ")))
        (route as CommandRoute.Executable).node.execution.execute(route.context).toCompletableFuture().join()
        return sent.joinToString("\n")
    }

    @Test
    fun `the command lists files under their identifiers`() {
        scope("quests").yaml<PrizeFile>("prizes.yml") { PrizeFile() }

        val text = run()

        assertTrue(text.contains("quests/main:prizes.yml"), text)
        assertTrue(text.contains("PrizeFile"), text)
    }

    @Test
    fun `info shows where the file lies and never the values`() {
        val config = scope("quests").yaml<PrizeFile>("prizes.yml") { PrizeFile() }
        config.load()

        val text = run("info", "quests/main:prizes.yml")

        assertTrue(text.contains("prizes.yml"), text)
        assertTrue(text.contains("загружен: да"), text)
        assertFalse(text.contains("CoinPrize@"), "values stay out of the report")
    }

    @Test
    fun `an unknown or malformed identifier is answered, not thrown`() {
        scope("quests").yaml<PrizeFile>("prizes.yml") { PrizeFile() }

        assertTrue(run("info", "quests/main:nope.yml").contains("Такого файла нет"))
        assertTrue(run("info", "quests").contains("Неверный адрес"))
    }

    @Test
    fun `reload reads the file again and reports a broken file without losing the old value`() {
        val config = scope("quests").yaml<PrizeFile>("prizes.yml") { PrizeFile() }
        config.load()
        assertTrue(run("reload", "quests/main:prizes.yml").contains("прочитан заново"))

        Files.writeString(folder.resolve("plugins/quests/prizes.yml"), "prizes: [unclosed")
        assertTrue(run("reload", "quests/main:prizes.yml").contains("не прочитан"))
        assertTrue(config.isLoaded)
    }

    @Test
    fun `validate says when a file is not loaded and when it is fine`() {
        val config = scope("quests").yaml<PrizeFile>("prizes.yml") { PrizeFile() }
        assertTrue(run("validate", "quests/main:prizes.yml").contains("не загружен"))

        config.load()
        assertTrue(run("validate", "quests/main:prizes.yml").contains("проблем нет"))
    }

    @Test
    fun `types lists the words of a polymorphic type and who added them`() {
        scope("quests").yaml<PrizeFile>("prizes.yml") { PrizeFile() }
        scope("questgems").type<Prize, GemPrize>().name("gem").into("quests").register()

        val text = run("types", "Prize")

        assertTrue(text.contains("coin"), text)
        assertTrue(text.contains("gem"), text)
        assertTrue(text.contains("questgems"), text)
        assertEquals(1, text.lines().count { it.contains("объявлен в самом типе") })
        assertTrue(run("types", "Nothing").contains("Такого типа нет"))
    }

    @Test
    fun `settings lists editable fields and edit changes one and saves the file`() {
        val config = scope("quests").yaml<TunedFile>("tuned.yml") { TunedFile() }
        config.load()

        val listing = run("settings", "quests/main:tuned.yml")
        assertTrue(listing.contains("greeting = Привет"), listing)
        assertTrue(listing.contains("нужен перезапуск"), listing)

        val done = run("edit", "quests/main:tuned.yml", "greeting", "Добро пожаловать сюда")
        assertTrue(done.contains("Файл сохранён"), done)
        assertEquals("Добро пожаловать сюда", config.get().greeting)

        val restart = run("edit", "quests/main:tuned.yml", "pool", "20")
        assertTrue(restart.contains("перезапуск"), restart)
    }

    @Test
    fun `edit answers a wrong value instead of throwing`() {
        scope("quests").yaml<TunedFile>("tuned.yml") { TunedFile() }.load()

        assertTrue(run("edit", "quests/main:tuned.yml", "pool", "many").contains("Не изменено"))
        assertTrue(run("edit", "quests/main:tuned.yml", "nothing", "1").contains("Такой настройки нет"))
    }
}
