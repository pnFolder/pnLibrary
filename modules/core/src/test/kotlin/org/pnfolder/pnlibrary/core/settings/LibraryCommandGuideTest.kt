package org.pnfolder.pnlibrary.core.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class LibraryCommandGuideTest {
    /** The permissions the Bukkit plugin declares; the working directory of a core test is the module folder. */
    private val declared: Set<String> = Files
        .readAllLines(Paths.get("../../platforms/bukkit/runtime/src/main/resources/plugin.yml"))
        .mapNotNull { Regex("""^  (pnlibrary\.[a-z.]+):\s*$""").find(it)?.groupValues?.get(1) }
        .toSet()

    @Test
    fun `every permission of plugin yml is in the guide`() {
        assertTrue(declared.size >= 10, "plugin.yml permissions were not found")
        val missing = declared - LibraryCommandGuide.permissions
        assertTrue(missing.isEmpty(), "Команды.txt does not list: $missing")
    }

    @Test
    fun `every permission of the guide is declared in plugin yml or belongs to the proxy only`() {
        val unknown = LibraryCommandGuide.permissions - declared - LibraryCommandGuide.proxyOnly
        assertTrue(unknown.isEmpty(), "not in plugin.yml and not marked proxy-only: $unknown")
        assertTrue(LibraryCommandGuide.proxyOnly.all { it !in declared }, "a proxy-only permission is declared for Bukkit")
    }

    @Test
    fun `every entry is complete and no command is listed twice`() {
        LibraryCommandGuide.entries.forEach { entry ->
            assertTrue(entry.usage.isNotBlank() && entry.description.isNotBlank(), entry.usage)
            assertTrue(entry.permission == null || entry.permission.startsWith("pnlibrary."), entry.usage)
        }
        val usages = LibraryCommandGuide.entries.map { it.usage }
        assertEquals(usages.size, usages.toSet().size, "a command is listed twice")
    }

    @Test
    fun `an entry without a permission says why`() {
        LibraryCommandGuide.entries.filter { it.permission == null }.forEach { assertTrue(it.note != null, it.usage) }
    }

    @Test
    fun `the guide names every command word of the control command`() {
        val words = LibraryCommandGuide.entries.map { it.usage.split(' ').getOrNull(1).orEmpty() }.toSet()
        listOf(
            "status", "check", "gui", "updates", "update", "events", "bus", "tasks", "stats", "placeholders", "settings", "configs", "deps",
            "debug", "servers", "groups", "money", "support",
        )
            .forEach { assertTrue(it in words, it) }
    }
}
