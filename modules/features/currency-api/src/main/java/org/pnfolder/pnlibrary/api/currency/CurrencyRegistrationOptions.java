package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

/** Options shared by lightweight, managed, and direct provider registrations. */
public interface CurrencyRegistrationOptions {
    /** Adds local aliases owned by the same plugin; conflicts fail during registration. */
    @NotNull
    CurrencyRegistrationOptions aliases(@NotNull String... aliases);
}
