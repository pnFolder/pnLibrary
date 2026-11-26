package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

/** Live currency registration that owns its provider and attached integrations. */
public interface CurrencyRegistration extends Currency, AutoCloseable {
    /** Current registration lifecycle state. */
    @NotNull
    CurrencyRegistrationState getState();

    /** Makes a disabled registration visible and operational again. */
    void enable();

    /** Temporarily hides the currency and makes mutations return unavailable results. */
    void disable();
}
