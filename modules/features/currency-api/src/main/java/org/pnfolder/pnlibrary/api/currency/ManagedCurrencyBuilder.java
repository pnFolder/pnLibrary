package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Definition builder for a storage-backed currency with an auditable ledger.
 *
 * <p>Exactly one storage method must be called before registration. Use {@link #storage} when several currencies
 * share a store whose lifecycle is managed elsewhere, or {@link #ownedStorage} when this registration should close it
 * automatically.
 */
public interface ManagedCurrencyBuilder extends CurrencyRegistrationOptions {
    /** Configures display and decimal rules. */
    @NotNull
    ManagedCurrencyBuilder descriptor(@NotNull Consumer<CurrencyDescriptor.Builder> configure);

    /** Uses shared storage without taking ownership of its lifecycle. */
    @NotNull
    ManagedCurrencyBuilder storage(@NotNull CurrencyStorage storage);

    /** Uses storage owned and closed by this currency registration. */
    @NotNull
    ManagedCurrencyBuilder ownedStorage(@NotNull CurrencyStorage storage);

    /** Sets the nonblank service identifier written to API-generated ledger entries. */
    @NotNull
    ManagedCurrencyBuilder service(@NotNull String name);

    /** Configures generated balance, formatted-value, and symbol placeholders. */
    @NotNull
    ManagedCurrencyBuilder placeholders(@NotNull Consumer<CurrencyPlaceholderOptions> configure);

    /** Configures platform command presentation and confirmation behavior. */
    @NotNull
    ManagedCurrencyBuilder commands(@NotNull Consumer<CurrencyCommandOptions> configure);
}
