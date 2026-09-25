package ru.privatenull.pnlibrary.api.currency

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.plugin.PluginId

class CurrencyAccessTest {
    @Test
    fun `patterns are normalized and blank patterns fail while building`() {
        val access = CurrencyAccess.builder().allowMatching("  shop-*  ").build()

        assertTrue(access.allows(PluginId.of("economy"), PluginId.of("shop-menu")))
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyAccess.builder().allowMatching(" ").build()
        }
    }
}
