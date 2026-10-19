package org.pnfolder.pnlibrary.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogCommands.SettingSaved

class BukkitTitleTest {
    @Test
    fun `a changed setting shows the old value in lilac and the new one in white`() {
        val title = BukkitTitle.settingSaved(SettingSaved("pnLibrary", "Канал", "stable", "beta"))

        assertEquals(BukkitTitle("§d✔ §fКанал", "§dstable§d → §fbeta"), title)
    }

    @Test
    fun `an unchanged setting shows only its value`() {
        assertEquals("§dвключена", BukkitTitle.settingSaved(SettingSaved("pnLibrary", "Автозагрузка", "включена", "включена")).subtitle)
    }

    @Test
    fun `pending updates downsample to legacy colors for 1_8 clients`() {
        assertEquals(BukkitTitle("§dОбновления pnFolder", "§fДоступно: §d3"), BukkitTitle.updatesPending(3))
    }
}
