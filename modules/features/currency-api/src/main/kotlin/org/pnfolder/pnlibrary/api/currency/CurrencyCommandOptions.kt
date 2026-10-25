package org.pnfolder.pnlibrary.api.currency

import java.time.Duration

/** Mutable command integration options exposed by [ManagedCurrencyBuilder.commands]. */
interface CurrencyCommandOptions {
    /** Enables or disables command integration. */
    fun enabled(value: Boolean): CurrencyCommandOptions
    /** Sets the permission root; blank values restore the generated default. */
    fun permissionPrefix(value: String): CurrencyCommandOptions
    /** Sets the response prefix component template. */
    fun prefix(value: String): CurrencyCommandOptions
    /** Sets the successful-operation template. */
    fun success(value: String): CurrencyCommandOptions
    /** Sets the rejected or failed operation template. */
    fun failure(value: String): CurrencyCommandOptions
    /** Sets the balance response template. */
    fun balance(value: String): CurrencyCommandOptions
    /** Sets the empty-history response. */
    fun historyEmpty(value: String): CurrencyCommandOptions
    /** Sets the destructive-operation confirmation policy. */
    fun confirmation(mode: CurrencyConfirmationMode): CurrencyCommandOptions
    /** Sets confirmation lifetime from 10 seconds through 10 minutes. */
    fun confirmationTimeout(value: Duration): CurrencyCommandOptions
}
