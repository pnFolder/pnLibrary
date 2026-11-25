package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.placeholders.info.PlaceholderInfo
import org.pnfolder.pnlibrary.api.registry.RegistryEntry
import org.pnfolder.pnlibrary.api.registry.Visibility
import org.pnfolder.pnlibrary.core.registry.ListRegistry

class PlaceholderRegistryTest {
    private val coins = PlaceholderInfo("shop.coins", "Coins", "shop/main", "Int", visibility = Visibility.PLUGIN)
    private val registry = ListRegistry("placeholders") { listOf(RegistryEntry(coins.address, coins, coins.owner, coins.visibility, coins.description)) }

    @Test
    fun `an entry carries the owner and the visibility of the placeholder`() {
        val entry = registry.entries().single()

        assertEquals("shop.coins", entry.key)
        assertEquals("shop/main", entry.owner)
        assertEquals(Visibility.PLUGIN, entry.visibility)
        assertEquals(coins, registry["shop.coins"])
    }

    @Test
    fun `an unknown address is absent and required throws`() {
        assertNull(registry["shop.gems"])
        assertFalse("shop.gems" in registry)
        assertThrows(NoSuchElementException::class.java) { registry.require("shop.gems") }
    }
}
