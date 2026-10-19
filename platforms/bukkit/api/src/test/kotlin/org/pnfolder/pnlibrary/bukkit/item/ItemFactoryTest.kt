package org.pnfolder.pnlibrary.bukkit.item

import org.bukkit.Material
import org.bukkit.configuration.MemoryConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify

class ItemFactoryTest {
    @Test
    fun `map and section apply the same overrides to decoded items`() {
        for (useSection in listOf(false, true)) {
            val meta = mock(ItemMeta::class.java)
            val item = TestItem(meta, 4)
            mockStatic(ItemStackCodec::class.java).use { codec ->
                codec.`when`<ItemStack> { ItemStackCodec.decode("saved") }.thenReturn(item)
                val values = mapOf("item_data" to "saved", "name" to "&bSword", "lore" to listOf("&7Details"))
                val result = if (useSection) {
                    val config = MemoryConfiguration()
                    values.forEach { (key, value) -> config.set(key, value) }
                    ItemFactory.fromSection(config)
                } else ItemFactory.fromMap(values)

                assertSame(item, result)
                assertEquals(4, result?.amount)
                verify(meta).setDisplayName("§bSword")
                verify(meta).setLore(listOf("§7Details"))
            }
        }
    }

    @Test
    fun `textured heads respect configured amount`() {
        val head = TestItem(null, 1)
        mockStatic(HeadUtil::class.java).use { heads ->
            heads.`when`<ItemStack> { HeadUtil.create("texture", "&fItem") }.thenReturn(head)
            assertEquals(12, ItemFactory.fromMap(mapOf("texture" to "texture", "amount" to 12))?.amount)
        }
    }

    @Test
    fun `exact writing preserves amount and leaves source untouched`() {
        val source = TestItem(null, 12)
        val parent = MemoryConfiguration()
        mockStatic(ItemStackCodec::class.java).use { codec ->
            codec.`when`<String> { ItemStackCodec.encode(org.mockito.ArgumentMatchers.any(ItemStack::class.java)) }
                .thenAnswer { invocation ->
                    assertEquals(12, invocation.getArgument<ItemStack>(0).amount)
                    "encoded-stack"
                }
            ItemFactory.writeExactItem(parent, "reward", source)
        }
        assertEquals(12, source.amount)
        assertEquals(12, parent.getInt("reward.amount"))
        assertEquals("encoded-stack", parent.getString("reward.item_data"))
    }

    /** Keeps these unit tests independent of a live Bukkit server. */
    private class TestItem(private val meta: ItemMeta?, amount: Int) : ItemStack(Material.STONE, amount) {
        override fun getItemMeta(): ItemMeta? = meta
        override fun setItemMeta(itemMeta: ItemMeta?): Boolean = true
        public override fun clone(): ItemStack = TestItem(meta, amount)
    }
}
