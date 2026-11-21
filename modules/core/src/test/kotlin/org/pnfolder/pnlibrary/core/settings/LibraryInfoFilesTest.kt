package org.pnfolder.pnlibrary.core.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LibraryInfoFilesTest {
    @TempDir lateinit var directory: Path

    private val warnings = mutableListOf<String>()
    private val plugin: Path get() = directory.resolve("plugins/pnLibrary")
    private val paths: LibraryPaths get() = LibraryPaths(plugin)
    private val store by lazy { SettingsStore(paths.settings) }

    private fun files(now: String = "2026-11-15T12:30:00Z", pluginPaths: LibraryPaths = paths) =
        LibraryInfoFiles(pluginPaths, store::values, "1.2.3", Clock.fixed(Instant.parse(now), ZoneOffset.UTC)) { warnings += it }

    private fun read(name: String): String = Files.readString(plugin.resolve(name), UTF_8)

    @Test
    fun `the help carries the current links, the commands, the version and the time`() {
        val text = files().helpText()

        assertTrue(PnLibraryIdentity.SUPPORT_URL in text)
        assertTrue(PnLibraryIdentity.REPOSITORY_URL in text)
        assertTrue("${PnLibraryIdentity.REPOSITORY_URL}/tree/main/docs" in text)
        listOf("/pn settings", "/pn debug", "/pn updates", "заказать", "data/", "Команды.txt", "Настройки.txt").forEach { assertTrue(it in text, it) }
        assertTrue("Этот файл создаётся заново при каждом запуске, ваши правки не сохранятся." in text)
        assertTrue(text.trimEnd().endsWith("${PnLibraryIdentity.NAME} 1.2.3\nСоздан: 2026-11-15 12:30 UTC"), text)
    }

    @Test
    fun `the links are the ones given, not remembered ones`() {
        val text = LibraryInfoFiles(paths, store::values, "1", supportUrl = "https://example.org/help", repositoryUrl = "https://example.org/code").helpText()

        assertTrue("https://example.org/help" in text)
        assertTrue("https://example.org/code/tree/main/docs" in text)
    }

    @Test
    fun `the overview lists every setting with its value, default, limits and description`() {
        val text = files().settingsText()

        SettingsRegistry.all.forEach { spec ->
            assertTrue(spec.key in text, spec.key)
            assertTrue(spec.title in text, spec.key)
            assertTrue(spec.description in text, spec.key)
            assertTrue(spec.display(spec.default) in text, spec.key)
            if (spec.range().isNotEmpty()) assertTrue(spec.range() in text, spec.key)
        }
        assertTrue("/pn settings set" in text)
        assertTrue("Этот файл создаётся заново" in text)
    }

    @Test
    fun `the overview shows the new value after a change`() {
        val files = files()
        files.refresh()
        assertTrue("Сейчас: 30  (по умолчанию)" in read("Настройки.txt"))

        store.onChange { files.refreshSettings() }
        store.set("history-retention-days", "120")

        assertTrue("Сейчас: 120  (изменено администратором)" in read("Настройки.txt"), read("Настройки.txt"))
        assertTrue(warnings.isEmpty(), warnings.toString())
    }

    @Test
    fun `the help keeps its list of useful commands short`() {
        val lines = files().helpText().lines()
        val start = lines.indexOf("Самые полезные команды")

        assertTrue(start >= 0)
        assertTrue(lines.drop(start + 1).takeWhile { it.startsWith("  /pn") }.size <= 8, lines.toString())
    }

    @Test
    fun `the command file has the header, every permission and the creation time`() {
        val text = files().commandsText()

        assertTrue("Все права по умолчанию у операторов; назначайте их в LuckPerms или другом менеджере прав." in text)
        assertTrue("Этот файл создаётся заново при каждом запуске, ваши правки не сохранятся." in text)
        LibraryCommandGuide.entries.forEach { entry ->
            assertTrue(entry.usage in text, entry.usage)
            entry.permission?.let { assertTrue(it in text, it) }
        }
        assertTrue(text.trimEnd().endsWith("${PnLibraryIdentity.NAME} 1.2.3\nСоздан: 2026-11-15 12:30 UTC"), text)
    }

    @Test
    fun `the three files are written as UTF-8 under their Russian names`() {
        files().refresh()

        assertTrue(Files.isRegularFile(plugin.resolve("Команды.txt")))
        assertFalse(Files.exists(plugin.resolve("COMMANDS.txt")))
        assertFalse(read("Команды.txt").contains('\r'))

        assertTrue(Files.isRegularFile(plugin.resolve("Помощь.txt")))
        assertTrue(Files.isRegularFile(plugin.resolve("Настройки.txt")))
        assertFalse(Files.exists(plugin.resolve("HELP.txt")))
        assertFalse(read("Помощь.txt").contains('\r'))
        assertEquals("Помощь pnLibrary", read("Помощь.txt").lines().first())
    }

    @Test
    fun `a start that changes nothing leaves the files alone, only the time line is ignored`() {
        files("2026-11-15T12:30:00Z").refresh()
        val before = read("Помощь.txt")

        files("2026-11-16T08:00:00Z").refresh()

        assertEquals(before, read("Помощь.txt"))
        assertTrue("Все права по умолчанию у операторов" in read("Команды.txt"))
    }

    @Test
    fun `a new version rewrites the files`() {
        files().refresh()
        LibraryInfoFiles(paths, store::values, "9.9.9").refresh()

        assertTrue("${PnLibraryIdentity.NAME} 9.9.9" in read("Помощь.txt"))
    }

    @Test
    fun `when the Russian name cannot be used the ASCII names are written`() {
        // A folder in the way makes the Russian name unusable, as an unmappable name does on a non-UTF-8 JVM.
        Files.createDirectories(plugin.resolve("Помощь.txt/inside"))
        Files.createDirectories(plugin.resolve("Настройки.txt/inside"))
        Files.createDirectories(plugin.resolve("Команды.txt/inside"))

        files().refresh()

        assertTrue(Files.isRegularFile(plugin.resolve("HELP.txt")))
        assertTrue(Files.isRegularFile(plugin.resolve("SETTINGS.txt")))
        assertTrue(Files.isRegularFile(plugin.resolve("COMMANDS.txt")))
        assertTrue(PnLibraryIdentity.SUPPORT_URL in read("HELP.txt"))
        assertTrue(warnings.isEmpty(), warnings.toString())
    }

    @Test
    fun `a failure never throws and is reported once`() {
        Files.createDirectories(directory.resolve("plugins"))
        Files.writeString(plugin, "not a folder")
        val files = files()

        files.refresh()
        files.refresh()

        assertEquals(1, warnings.size, warnings.toString())
    }

    @Test
    fun `switches are written in Russian in the overview`() {
        val text = files().settingsText()

        assertTrue("Сейчас: выключено  (по умолчанию)" in text, text)
        assertTrue("По умолчанию: включено" in text, text)
        assertTrue("/pn settings set privacy включено" in text, text)
        assertFalse(Regex("""(Сейчас|По умолчанию): (true|false)""").containsMatchIn(text), text)
    }
}
