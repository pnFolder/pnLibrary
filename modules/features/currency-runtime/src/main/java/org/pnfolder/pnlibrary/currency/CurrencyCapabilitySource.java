package org.pnfolder.pnlibrary.currency;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyCapability;

/** Internal capability declaration used when a provider implements optional interfaces structurally. */
interface CurrencyCapabilitySource {
    /** Operations the provider really supports, which may be fewer than the interfaces it implements. */
    @NotNull
    Set<CurrencyCapability> getDeclaredCapabilities();
}
