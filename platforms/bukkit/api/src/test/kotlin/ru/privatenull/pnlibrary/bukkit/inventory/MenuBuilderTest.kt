package ru.privatenull.pnlibrary.bukkit.inventory

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MenuBuilderTest {
    @Test
    fun `chest validates size and builds border`() {
        val menu = Menus.chest("Test").rows(3).border(ItemStack(Material.STONE)).build()
        assertEquals(27, menu.size)
        assertEquals(20, menu.items.size)
        assertThrows(IllegalArgumentException::class.java) { Menus.chest("Bad").rows(7) }
    }

    @Test
    fun `public callbacks do not expose Kotlin function types`() {
        val types = listOf(MenuBuilder::class.java, MenuService::class.java, MenuSession::class.java)
            .flatMap { it.declaredMethods.toList() }
            .flatMap { method -> method.parameterTypes.toList() + method.returnType }
            .map { it.name }
        assertFalse(types.any { it.startsWith("kotlin.jvm.functions.") })
    }
}
