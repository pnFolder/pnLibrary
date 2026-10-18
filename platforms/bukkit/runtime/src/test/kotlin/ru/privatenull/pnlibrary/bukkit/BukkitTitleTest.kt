package ru.privatenull.pnlibrary.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.core.updates.catalog.UpdateCatalogCommands.SettingSaved

class BukkitTitleTest {
    @Test
    fun `a changed setting shows the old value in sky blue and the new one in white`() {
        val title = BukkitTitle.settingSaved(SettingSaved("pnLibrary", "Канал", "stable", "beta"))

        assertEquals(BukkitTitle("§d✔ §fКанал", "§bstable§d → §fbeta"), title)
    }

    @Test
    fun `an unchanged setting shows only its value`() {
        assertEquals("§bвключена", BukkitTitle.settingSaved(SettingSaved("pnLibrary", "Автозагрузка", "включена", "включена")).subtitle)
    }

    @Test
    fun `pending updates downsample to legacy colors for 1_8 clients`() {
        assertEquals(BukkitTitle("§dОбновления pnFolder", "§fДоступно: §b3"), BukkitTitle.updatesPending(3))
    }
}
