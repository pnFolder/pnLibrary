package org.pnfolder.pnlibrary.currency;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyBalanceMutation;
import org.pnfolder.pnlibrary.api.currency.CurrencyCapability;
import org.pnfolder.pnlibrary.api.currency.CurrencyDeposits;
import org.pnfolder.pnlibrary.api.currency.CurrencyFormatting;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyReset;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransfers;
import org.pnfolder.pnlibrary.api.currency.CurrencyWithdrawals;

/** Which operations a provider supports. */
final class CurrencyCapabilities {
    private CurrencyCapabilities() {
    }

    /** Capabilities of {@code provider}: those it declares, else one per optional interface it implements. */
    @NotNull
    static Set<CurrencyCapability> of(@NotNull CurrencyProvider provider) {
        if (provider instanceof CurrencyCapabilitySource) {
            return ((CurrencyCapabilitySource) provider).getDeclaredCapabilities();
        }
        Set<CurrencyCapability> capabilities = new LinkedHashSet<>();
        capabilities.add(CurrencyCapability.BALANCE);
        if (provider instanceof CurrencyDeposits) {
            capabilities.add(CurrencyCapability.DEPOSIT);
        }
        if (provider instanceof CurrencyWithdrawals) {
            capabilities.add(CurrencyCapability.WITHDRAW);
        }
        if (provider instanceof CurrencyBalanceMutation) {
            capabilities.add(CurrencyCapability.SET_BALANCE);
        }
        if (provider instanceof CurrencyReset) {
            capabilities.add(CurrencyCapability.RESET);
        }
        if (provider instanceof CurrencyTransfers) {
            capabilities.add(CurrencyCapability.TRANSFER);
        }
        if (provider instanceof CurrencyFormatting) {
            capabilities.add(CurrencyCapability.FORMATTING);
        }
        return capabilities;
    }
}
