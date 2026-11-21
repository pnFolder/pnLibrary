package org.pnfolder.pnlibrary.core.menu.style

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.core.menu.screens.ConfigRow

class ConfigLookTest {
    private fun plain(text: String) = text.replace(Regex("§."), "")

    @Test
    fun `a path is cut between its parts and never inside a name`() {
        val path = "C:/Users/lpvan/Downloads/Server/Paper-Standalone/plugins/pnTest/external"

        val lines = ConfigLook.fact("Папка", path)

        assertEquals(path, lines.joinToString("") { it.second })
        assertEquals("Папка", lines.first().first)
        assertTrue(lines.drop(1).all { it.first.isEmpty() })
        assertTrue(lines.dropLast(1).all { it.second.endsWith("/") })
        assertTrue(lines.all { it.second.length <= 38 })
    }

    @Test
    fun `a class name breaks after its dots and a sentence keeps its words whole`() {
        val classLines = ConfigLook.fact("Класс", "org.pnfolder.pnlibrary.example.configuration.VeryLongConfigurationClassName")
        assertEquals("org.pnfolder.pnlibrary.example.configuration.VeryLongConfigurationClassName", classLines.joinToString("") { it.second })
        assertTrue(classLines.dropLast(1).all { it.second.endsWith(".") })

        val words = "очень длинное значение которое никак не помещается в одну строку описания предмета"
        val lines = ConfigLook.fact("Значение", words).map { it.second }
        assertEquals(words.split(" "), lines.flatMap { it.split(" ") })
    }

    @Test
    fun `a word longer than a whole line is cut as the last resort`() {
        val lines = ConfigLook.softWrap("a".repeat(100), 20, 30)

        assertEquals("a".repeat(100), lines.joinToString(""))
        assertTrue(lines.drop(1).all { it.length <= 30 } && lines.first().length <= 20)
    }

    @Test
    fun `a file shows its own name and names its plugin and module only when asked`() {
        val row = ConfigRow("shop/main:sub/rewards.yml", "dev.shop.RewardsConfig", true, false, 2, null)

        val alone = ConfigLook.file(row, "shop", "main", "sub/rewards.yml", withPlugin = false, withModule = false)
        val mixed = ConfigLook.file(row, "shop", "main", "sub/rewards.yml", withPlugin = true, withModule = true)

        assertTrue(plain(alone.name).endsWith("rewards.yml") && !plain(alone.name).contains("shop"))
        assertTrue(alone.lore.none { plain(it).contains("Плагин") })
        assertTrue(mixed.lore.any { plain(it).contains("Плагин") } && mixed.lore.any { plain(it).contains("Модуль") })
        assertTrue(mixed.lore.any { plain(it).contains("Папка") })
        assertTrue(mixed.lore.any { plain(it).contains("dev.shop.RewardsConfig") })
    }
}
