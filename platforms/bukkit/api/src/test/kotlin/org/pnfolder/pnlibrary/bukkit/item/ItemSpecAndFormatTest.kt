package org.pnfolder.pnlibrary.bukkit.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemSpecAndFormatTest {
    @Test
    fun `a new spec is one stone`() {
        val spec = itemSpec { }

        assertEquals("STONE", spec.material)
        assertEquals(1, spec.amount)
        assertNull(spec.name)
        assertTrue(spec.lore.isEmpty())
    }

    @Test
    fun `values are trimmed and lore is replaced or extended`() {
        val spec = itemSpec {
            material(" DIAMOND_SWORD ")
            lore("a", "b")
            addLore("c")
            enchant(" sharpness ", 5)
            texture("  ")
        }

        assertEquals("DIAMOND_SWORD", spec.material)
        assertEquals(listOf("a", "b", "c"), spec.lore)
        assertEquals(mapOf("sharpness" to 5), spec.enchantments)
        assertNull(spec.texture)
    }

    @Test
    fun `bad values are refused`() {
        assertThrows(IllegalArgumentException::class.java) { itemSpec { material(" ") } }
        assertThrows(IllegalArgumentException::class.java) { itemSpec { amount(0) } }
        assertThrows(IllegalArgumentException::class.java) { itemSpec { enchant("", 1) } }
    }

    @Test
    fun `a built spec does not change when its builder does`() {
        val builder = ItemSpec.builder().lore("a")
        val spec = builder.build()
        builder.addLore("b")

        assertEquals(listOf("a"), spec.lore)
        assertThrows(UnsupportedOperationException::class.java) { (spec.lore as MutableList<String>).add("x") }
    }

    @Test
    fun `toBuilder keeps everything and customizers run in order`() {
        val seen = mutableListOf<String>()
        val spec = itemSpec {
            material("APPLE")
            amount(3)
            name("n")
            enchant("sharpness", 2)
            customize { seen += "first" }
            customize { seen += "second" }
        }

        val copy = spec.toBuilder().amount(5).build()

        assertEquals("APPLE", copy.material)
        assertEquals(5, copy.amount)
        assertEquals(3, spec.amount)
        assertEquals("n", copy.name)
        assertEquals(mapOf("sharpness" to 2), copy.enchantments)
        assertEquals(2, copy.customizers.size)
    }

    @Test
    fun `the default format has the documented keys`() {
        val format = ItemFormat.DEFAULT

        assertEquals("material", format.material)
        assertEquals("amount", format.amount)
        assertEquals("name", format.name)
        assertEquals("lore", format.lore)
        assertEquals("enchantments", format.enchantments)
        assertEquals(listOf("base64", "texture"), format.textures)
        assertEquals("item_data", format.exact)
    }

    @Test
    fun `a format changes only the keys it names and keeps its own fields`() {
        val format = itemFormat {
            name(" display-name ")
            texture("skin")
            field("glow") { _, _ -> }
        }

        assertEquals("display-name", format.name)
        assertEquals("material", format.material)
        assertEquals(listOf("skin"), format.textures)
        assertEquals(listOf("glow"), format.fields.keys.toList())

        val copy = format.toBuilder().lore("description").build()
        assertEquals("description", copy.lore)
        assertEquals("display-name", copy.name)
        assertEquals(listOf("glow"), copy.fields.keys.toList())
    }

    @Test
    fun `a format refuses blank keys and an empty texture list`() {
        assertThrows(IllegalArgumentException::class.java) { itemFormat { material(" ") } }
        assertThrows(IllegalArgumentException::class.java) { itemFormat { texture() } }
    }
}
