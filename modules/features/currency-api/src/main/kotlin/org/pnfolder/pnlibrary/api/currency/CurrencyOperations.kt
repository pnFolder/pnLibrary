package org.pnfolder.pnlibrary.api.currency

import java.math.BigDecimal
import java.util.concurrent.CompletionStage
import java.util.function.BiFunction
import java.util.function.Function

/** Java-friendly operation builder for a lightweight currency provider. */
interface CurrencyOperations {
    /** Configures the mandatory synchronous balance lookup. */
    fun balance(operation: Function<CurrencyAccount, BigDecimal>): CurrencyOperations
    /** Configures the mandatory asynchronous balance lookup. */
    fun balanceAsync(operation: Function<CurrencyAccount, CompletionStage<BigDecimal>>): CurrencyOperations
    /** Configures synchronous deposit support. */
    fun deposit(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyOperations
    /** Configures asynchronous deposit support. */
    fun depositAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>): CurrencyOperations
    /** Configures synchronous withdrawal support. */
    fun withdraw(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyOperations
    /** Configures asynchronous withdrawal support. */
    fun withdrawAsync(operation: BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>>): CurrencyOperations
    /** Configures synchronous set-balance support. */
    fun setBalance(operation: BiFunction<CurrencyAccount, BigDecimal, CurrencyResult>): CurrencyOperations
    /** Configures synchronous reset support. */
    fun reset(operation: Function<CurrencyAccount, CurrencyResult>): CurrencyOperations
    /** Configures synchronous atomic transfer support. */
    fun transfer(operation: CurrencyTransferOperation): CurrencyOperations
    /** Configures provider-specific amount presentation. */
    fun format(operation: Function<BigDecimal, String>): CurrencyOperations
    /** Publishes [value] as an advanced extension under exact [type]. */
    fun extension(type: Class<*>, value: Any): CurrencyOperations
}
