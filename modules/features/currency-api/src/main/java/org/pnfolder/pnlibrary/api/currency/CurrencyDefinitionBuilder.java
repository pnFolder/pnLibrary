package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/** Definition builder for a lightweight currency backed by caller-supplied operations. */
public interface CurrencyDefinitionBuilder extends CurrencyRegistrationOptions {
    /** Configures display and decimal rules. */
    @NotNull
    CurrencyDefinitionBuilder descriptor(@NotNull Consumer<CurrencyDescriptor.Builder> configure);

    /** Configures mandatory balance lookup and optional mutation capabilities. */
    @NotNull
    CurrencyDefinitionBuilder operations(@NotNull Consumer<CurrencyOperations> configure);
}
