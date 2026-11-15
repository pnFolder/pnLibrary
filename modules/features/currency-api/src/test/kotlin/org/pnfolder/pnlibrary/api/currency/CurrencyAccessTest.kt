package org.pnfolder.pnlibrary.api.currency

import org.pnfolder.pnlibrary.api.plugin.PluginAccess

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.plugin.PluginId

class CurrencyAccessTest {
    @Test
    fun `patterns are normalized and blank patterns fail while building`() {
        val access = PluginAccess.builder().allowMatching("  shop-*  ").build()

        assertTrue(access.allows(PluginId.of("economy"), PluginId.of("shop-menu")))
        assertThrows(IllegalArgumentException::class.java) {
            PluginAccess.builder().allowMatching(" ").build()
        }
    }
}
