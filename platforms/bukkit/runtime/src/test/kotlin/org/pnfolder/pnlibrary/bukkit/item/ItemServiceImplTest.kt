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

class ItemServiceImplTest {
    @Test
    fun `a format renames the keys and reads fields of its own into the same spec`() {
        val seen = mutableListOf<Any>()
        val format = itemFormat {
            material("type")
            name("display-name")
            lore("description")
            enchantments("magic")
            field("glow") { item, value -> if (value == true) item.enchant("unbreaking", 1) }
            field("model") { item, value -> item.customize { seen += value } }
        }
        val config = MemoryConfiguration().apply {
            set("type", "DIAMOND_SWORD")
            set("amount", 3)
            set("display-name", "&bМеч")
            set("description", "&7одна строка")
            set("magic.sharpness", 5)
            set("glow", true)
            set("model", 42)
            set("name", "не тот ключ")
        }

        val spec = ItemServiceImpl.read(config, format)!!

        assertEquals("DIAMOND_SWORD", spec.material)
        assertEquals(3, spec.amount)
        assertEquals("&bМеч", spec.name)
        assertEquals(listOf("&7одна строка"), spec.lore)
        assertEquals(mapOf("sharpness" to 5, "unbreaking" to 1), spec.enchantments)
        assertEquals(1, spec.customizers.size)
        spec.customizers.single().apply(TestItem(null, 1))
        assertEquals(listOf<Any>(42), seen)
    }

    @Test
    fun `the default format keeps its keys and a base64 material becomes a texture`() {
        val spec = ItemServiceImpl.read(mapOf("material" to "base64:abc", "texture" to "hash", "lore" to listOf("a", "b")))!!
        assertEquals("hash", spec.texture)
        assertEquals(listOf("a", "b"), spec.lore)
        assertEquals("STONE", spec.material)
    }

    @Test
    fun `a spec made by hand can be changed through its builder without touching the original`() {
        val base = itemSpec { material("APPLE"); amount(2); name("&aЯблоко"); lore("одна") }
        val changed = base.toBuilder().amount(5).addLore("вторая").build()
        assertEquals(2, base.amount)
        assertEquals(listOf("одна"), base.lore)
        assertEquals(5, changed.amount)
        assertEquals(listOf("одна", "вторая"), changed.lore)
        assertEquals("APPLE", changed.material)
    }

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
                    ItemServiceImpl.fromSection(config)
                } else ItemServiceImpl.fromMap(values)

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
        mockStatic(PlayerHeads::class.java).use { heads ->
            heads.`when`<ItemStack> { PlayerHeads.create("texture", "&fItem") }.thenReturn(head)
            assertEquals(12, ItemServiceImpl.fromMap(mapOf("texture" to "texture", "amount" to 12))?.amount)
        }
    }

    @Test
    fun `exact writing preserves amount and leaves source untouched`() {
        val source = TestItem(null, 12)
        val parent = MemoryConfiguration()
        mockStatic(ItemStackCodec::class.java).use { codec ->
            codec.`when`<String> { ItemStackCodec.encode(org.mockito.ArgumentMatchers.any(ItemStack::class.java) ?: TestItem(null, 1)) }
                .thenAnswer { invocation ->
                    assertEquals(12, invocation.getArgument<ItemStack>(0).amount)
                    "encoded-stack"
                }
            ItemServiceImpl.writeExact(parent, "reward", source)
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
