package ru.privatenull.pnlibrary.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemUtilitiesTest {
    @Test
    fun `item codec rejects null input`() {
        assertThrows(IllegalArgumentException::class.java) { ItemStackCodec.encode(null) }
    }

    @Test
    fun `item codec rejects malformed payloads`() {
        assertThrows(IllegalArgumentException::class.java) { ItemStackCodec.decode("not-base64") }
    }

    @Test
    fun `texture normalization accepts hashes urls and base64`() {
        val hash = "0123456789abcdef0123456789abcdef"
        val url = "https://textures.minecraft.net/texture/$hash"
        val encoded = java.util.Base64.getEncoder().encodeToString(
            "{\"textures\":{\"SKIN\":{\"url\":\"$url\"}}}".toByteArray(),
        )

        assertEquals(hash, HeadUtil.normalizeTexture(hash))
        assertEquals(url, HeadUtil.normalizeTexture(url))
        assertEquals(encoded, HeadUtil.normalizeTexture("base64:$encoded"))
        assertNull(HeadUtil.normalizeTexture("not a texture"))
    }

    @Test
    fun `real item check excludes null air and empty stacks`() {
        assertFalse(ItemFactory.isRealItem(null))
        assertFalse(ItemFactory.isRealItem(ItemStack(Material.AIR)))
        assertTrue(ItemFactory.isRealItem(ItemStack(Material.STONE)))
    }
}
