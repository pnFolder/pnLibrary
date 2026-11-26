package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/** Mutable command integration options exposed by {@link ManagedCurrencyBuilder#commands}. */
public interface CurrencyCommandOptions {
    /** Enables or disables command integration. */
    @NotNull
    CurrencyCommandOptions enabled(boolean value);

    /** Sets the permission root; blank values restore the generated default. */
    @NotNull
    CurrencyCommandOptions permissionPrefix(@NotNull String value);

    /** Sets the response prefix component template. */
    @NotNull
    CurrencyCommandOptions prefix(@NotNull String value);

    /** Sets the successful-operation template. */
    @NotNull
    CurrencyCommandOptions success(@NotNull String value);

    /** Sets the rejected or failed operation template. */
    @NotNull
    CurrencyCommandOptions failure(@NotNull String value);

    /** Sets the balance response template. */
    @NotNull
    CurrencyCommandOptions balance(@NotNull String value);

    /** Sets the empty-history response. */
    @NotNull
    CurrencyCommandOptions historyEmpty(@NotNull String value);

    /** Sets the destructive-operation confirmation policy. */
    @NotNull
    CurrencyCommandOptions confirmation(@NotNull CurrencyConfirmationMode mode);

    /** Sets confirmation lifetime from 10 seconds through 10 minutes. */
    @NotNull
    CurrencyCommandOptions confirmationTimeout(@NotNull Duration value);
}
