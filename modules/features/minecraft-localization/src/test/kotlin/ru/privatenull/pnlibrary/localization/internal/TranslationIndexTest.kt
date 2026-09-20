package ru.privatenull.pnlibrary.localization.internal

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TranslationIndexTest {
    @Test fun `search normalizes russian spelling and ranks exact before prefix`() {
        val index = TranslationIndexImpl.materials(linkedMapOf(
            "item.minecraft.stone" to "Камень",
            "item.minecraft.stone_sword" to "Каменный меч",
            "item.minecraft.diamond_sword" to "Алмазный меч",
        ))
        assertEquals(Material.STONE, index.findExact("  КАМЕНЬ ").single().value)
        assertEquals(listOf("item.minecraft.stone", "item.minecraft.stone_sword"), index.search("камен").map { it.key })
        assertEquals(TranslationIndexImpl.normalize("Всё"), TranslationIndexImpl.normalize("ВСЕ"))
    }

    @Test fun `exact lookup preserves collisions`() {
        val index = TranslationIndexImpl.keys(mapOf("first" to "Одинаково", "second" to "Одинаково"))
        assertEquals(setOf("first", "second"), index.findExact("одинаково").map { it.key }.toSet())
    }
}
