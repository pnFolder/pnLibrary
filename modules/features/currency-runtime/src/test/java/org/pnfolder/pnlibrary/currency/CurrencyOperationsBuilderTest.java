package org.pnfolder.pnlibrary.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyCapability;
import org.pnfolder.pnlibrary.api.currency.CurrencyDeposits;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;

class CurrencyOperationsBuilderTest {
    @Test
    void buildRequiresBalanceOperation() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new CurrencyOperationsBuilder().build(descriptor(), "coins"));

        assertEquals("Currency coins requires a balance operation", error.getMessage());
    }

    @Test
    void capabilitiesContainOnlyConfiguredOperations() {
        CurrencyProvider provider = new CurrencyOperationsBuilder()
                .balance(account -> BigDecimal.TEN)
                .deposit((account, amount) -> CurrencyResult.success(null, amount))
                .build(descriptor(), "coins");
        Set<CurrencyCapability> capabilities = ((CurrencyCapabilitySource) provider).getDeclaredCapabilities();

        assertTrue(capabilities.contains(CurrencyCapability.BALANCE));
        assertTrue(capabilities.contains(CurrencyCapability.DEPOSIT));
        assertFalse(capabilities.contains(CurrencyCapability.WITHDRAW));
        assertEquals(BigDecimal.TEN, provider.balance(new CurrencyAccount(UUID.randomUUID())).toCompletableFuture().join());
        assertTrue(((CurrencyDeposits) provider).deposit(new CurrencyAccount(UUID.randomUUID()), BigDecimal.ONE)
                .toCompletableFuture().join().isSuccess());
    }

    @Test
    void extensionPreservesExactRegisteredType() {
        Runnable extension = () -> { };
        CurrencyProvider provider = new CurrencyOperationsBuilder()
                .balance(account -> BigDecimal.ZERO)
                .extension(Runnable.class, extension)
                .build(descriptor(), "coins");

        assertSame(extension, provider.extension(Runnable.class));
    }

    private static CurrencyDescriptor descriptor() {
        return new CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN);
    }
}
