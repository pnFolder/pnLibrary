package org.pnfolder.pnlibrary.bukkit.currency

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VaultCurrencyStartupTest {
    @Test
    fun `economy that is still starting is not connected yet`() {
        assertNull(BukkitCurrencyAdapters.vaultCurrency(StartingEconomy()))
    }

    @Test
    fun `ready economy is connected`() {
        assertNotNull(BukkitCurrencyAdapters.vaultCurrency(ReadyEconomy()))
    }

    @Test
    fun `other provider failures are not hidden`() {
        assertThrows<java.lang.reflect.InvocationTargetException> {
            BukkitCurrencyAdapters.vaultCurrency(BrokenEconomy())
        }
    }

    class StartingEconomy {
        fun currencyNamePlural(): String = throw NullPointerException("settings are not loaded")
    }

    class ReadyEconomy {
        fun currencyNamePlural(): String = "Coins"
    }

    class BrokenEconomy {
        fun currencyNamePlural(): String = throw IllegalStateException("broken")
    }
}
