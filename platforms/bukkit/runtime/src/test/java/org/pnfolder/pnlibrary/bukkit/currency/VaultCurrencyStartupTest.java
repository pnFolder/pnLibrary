package org.pnfolder.pnlibrary.bukkit.currency;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

class VaultCurrencyStartupTest {
    @Test
    void economyThatIsStillStartingIsNotConnectedYet() throws Exception {
        assertNull(BukkitCurrencyAdapters.vaultCurrency(new StartingEconomy()));
    }

    @Test
    void readyEconomyIsConnected() throws Exception {
        assertNotNull(BukkitCurrencyAdapters.vaultCurrency(new ReadyEconomy()));
    }

    @Test
    void otherProviderFailuresAreNotHidden() {
        assertThrows(InvocationTargetException.class, () -> BukkitCurrencyAdapters.vaultCurrency(new BrokenEconomy()));
    }

    public static class StartingEconomy {
        public String currencyNamePlural() {
            throw new NullPointerException("settings are not loaded");
        }
    }

    public static class ReadyEconomy {
        public String currencyNamePlural() {
            return "Coins";
        }
    }

    public static class BrokenEconomy {
        public String currencyNamePlural() {
            throw new IllegalStateException("broken");
        }
    }
}
