package org.pnfolder.pnlibrary.core.commands.settings

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.core.commands.engine.CommandRoute
import org.pnfolder.pnlibrary.core.commands.engine.CommandTreeRouter
import org.pnfolder.pnlibrary.core.settings.SettingPickerHook
import org.pnfolder.pnlibrary.core.settings.SettingsStore
import java.nio.file.Path

class SettingsCommandTest {
    @TempDir lateinit var directory: Path

    private val store by lazy { SettingsStore(directory.resolve("data/settings.json")) }
    private val hook = SettingPickerHook()
    private val definition by lazy { SettingsCommand(store, hook).definition() }
    private var console = true
    private val sent = mutableListOf<String>()
    private val sender = object : CommandSender {
        override val id = "console"
        override val name = "Console"
        override val isConsole get() = console
        override fun hasPermission(permission: String) = true
        override fun send(message: Component) {
            sent += PlainTextComponentSerializer.plainText().serialize(message)
        }
    }

    private fun run(vararg words: String): String {
        sent.clear()
        val route = CommandTreeRouter().route(definition, CommandContext(sender, words.toList(), "settings", words.joinToString(" ")))
        (route as CommandRoute.Executable).node.execution.execute(route.context).toCompletableFuture().join()
        return sent.joinToString("\n")
    }

    @Test
    fun `the list shows every setting with its value and marks the changed ones`() {
        store.set("privacy", "true")

        val first = run("list")
        val second = run("list", "2")

        assertTrue(first.contains("privacy = включено  (по умолчанию выключено)"), first)
        assertTrue(first.contains("history-retention-days = 30"), first)
        assertTrue(second.contains("страница 2/2"), second)
        assertTrue(second.contains("updates.read-timeout = 30с"), second)
        assertEquals(first, run(), "the bare command is the list")
    }

    @Test
    fun `get shows value default limits and when a change works`() {
        val text = run("get", "history-retention-days")

        assertTrue(text.contains("значение: 30 (по умолчанию)"), text)
        assertTrue(text.contains("допустимо: от 30 до 365"), text)
        assertTrue(text.contains("заработает: после перезапуска"), text)
        assertTrue(run("get", "downloads.enabled").contains("заработает: сразу"))
        assertTrue(run("get", "nope").contains("Такой настройки нет"))
    }

    @Test
    fun `set saves the value and says when it starts to work`() {
        val live = run("set", "downloads.automatic", "off")
        val later = run("set", "history-retention-days", "90")

        assertFalse(store.current().downloadsAutomatic)
        assertEquals(90, store.current().historyRetentionDays)
        assertTrue(live.contains("downloads.automatic: включено → выключено. Сохранено."), live)
        assertFalse(live.contains("перезапуска"), live)
        assertTrue(later.contains("30 → 90"), later)
        assertTrue(later.contains("после перезапуска"), later)
    }

    @Test
    fun `a text with spaces is taken whole`() {
        run("set", "no-permission-message", "Нет", "права", "на", "это")

        assertEquals("Нет права на это", store.current().noPermissionMessage)
    }

    @Test
    fun `a wrong value or key is answered with the reason and nothing changes`() {
        val range = run("set", "history-retention-days", "7")
        val word = run("set", "privacy", "maybe")
        val time = run("set", "updates.check-interval", "5m")
        val key = run("set", "nope", "1")

        assertTrue(range.contains("Не изменено") && range.contains("от 30 до 365"), range)
        assertTrue(word.contains("Не изменено") && word.contains("вкл или выкл"), word)
        assertTrue(time.contains("Не изменено") && time.contains("вне допустимых границ"), time)
        assertTrue(key.contains("Такой настройки нет"), key)
        assertEquals(org.pnfolder.pnlibrary.core.settings.LibrarySettings(), store.current())
    }

    @Test
    fun `reset returns a setting to its default`() {
        run("set", "privacy", "true")

        val text = run("reset", "privacy")

        assertTrue(text.contains("включено → выключено"), text)
        assertFalse(store.current().privacy)
        assertTrue(run("reset", "nope").contains("Такой настройки нет"))
    }

    private fun suggest(vararg words: String): List<String> =
        CommandTreeRouter().suggest(definition, CommandContext(sender, words.toList(), "settings", words.joinToString(" "))).toCompletableFuture().join()

    @Test
    fun `keys and values are suggested`() {
        val keys = suggest("set", "")
        assertTrue("downloads.enabled" in keys && "privacy" in keys && "updates.check-interval" in keys, keys.toString())
        assertEquals(listOf("downloads.destinations.cache", "downloads.destinations.data-folder"), suggest("get", "downloads.dest").sorted())
        assertEquals(listOf("включено", "выключено"), suggest("set", "privacy", "").sorted())
        assertTrue("6h" in suggest("set", "updates.check-interval", ""))
        run("set", "privacy", "true")
        assertEquals(listOf("privacy"), suggest("reset", ""))
    }

    @Test
    fun `a switch is shown and understood in Russian everywhere in the command`() {
        assertTrue(run("list").contains("privacy = выключено"), "the list")
        val get = run("get", "privacy")
        assertTrue(get.contains("значение: выключено (по умолчанию)"), get)
        assertFalse(get.contains("false") || get.contains("true"), get)
        listOf("вкл", "включено", "да", "on", "true").forEach { word ->
            run("reset", "privacy")
            run("set", "privacy", word)
            assertTrue(store.current().privacy, word)
        }
        listOf("выкл", "выключено", "нет", "off", "false").forEach { word ->
            run("set", "privacy", "включено")
            run("set", "privacy", word)
            assertFalse(store.current().privacy, word)
        }
        run("set", "privacy", "включено")
        assertTrue(run("get", "privacy").contains("по умолчанию: выключено"))
    }

    @Test
    fun `a time zone is completed from the list of zones`() {
        val zones = suggest("set", "time-zone", "")

        assertTrue("Europe/Moscow" in zones && "UTC" in zones && "America/New_York" in zones, zones.size.toString())
        assertEquals(zones.size, zones.toSet().size)
    }

    @Test
    fun `pick opens the list of a choice setting for a player with the words after the key as the filter`() {
        console = false
        val opened = mutableListOf<Pair<String, String>>()
        hook.opener = { _, key, filter -> opened.add(key to filter) }

        run("pick", "time-zone")
        run("pick", "time-zone", "new", "york")

        assertEquals(listOf("time-zone" to "", "time-zone" to "new york"), opened)
    }

    @Test
    fun `pick refuses the console, a setting without a list and a platform without menus`() {
        val opened = mutableListOf<String>()
        hook.opener = { _, key, _ -> opened.add(key) }

        assertTrue(run("pick", "time-zone").contains("меню игрока"))
        console = false
        assertTrue(run("pick", "privacy").contains("Нет настройки с выбором из списка"))
        hook.opener = null
        assertTrue(run("pick", "time-zone").contains("Меню здесь недоступно"))
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `pick completes only the settings that have a list`() {
        assertEquals(listOf("time-zone"), suggest("pick", ""))
    }
}
