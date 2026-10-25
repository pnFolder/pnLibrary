package org.pnfolder.pnlibrary.api.currency

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletionStage

/**
 * Consumer-facing asynchronous facade for one registered currency.
 *
 * Amount mutations reject values that violate [descriptor] precision. Deposit,
 * withdrawal, and transfer require positive amounts; set-balance accepts zero.
 * Unsupported or inactive mutations complete normally with a structured [CurrencyResult].
 */
interface Currency {
    /** Globally unique registration key. */
    val key: CurrencyKey
    /** Presentation and decimal rules applied by this facade. */
    val descriptor: CurrencyDescriptor
    /** Immutable set of operations supported by the underlying provider. */
    val capabilities: Set<CurrencyCapability>
    /** Returns whether the provider exposes [capability]. */
    fun supports(capability: CurrencyCapability) = capability in capabilities
    /** Returns a normalized balance for [playerId]. */
    fun balance(playerId: UUID): CompletionStage<BigDecimal>
    /** Returns whether the normalized balance covers positive [amount]. */
    fun has(playerId: UUID, amount: BigDecimal): CompletionStage<Boolean>
    /** Attempts to add positive [amount] to the player's balance. */
    fun deposit(playerId: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    /** Long convenience overload for [deposit]. */
    fun deposit(playerId: UUID, amount: Long): CompletionStage<CurrencyResult> = deposit(playerId, BigDecimal.valueOf(amount))
    /** Attempts to subtract positive [amount] from the player's balance. */
    fun withdraw(playerId: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    /** Long convenience overload for [withdraw]. */
    fun withdraw(playerId: UUID, amount: Long): CompletionStage<CurrencyResult> = withdraw(playerId, BigDecimal.valueOf(amount))
    /** Attempts to replace the player's balance with non-negative [amount]. */
    fun setBalance(playerId: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    /** Long convenience overload for [setBalance]. */
    fun setBalance(playerId: UUID, amount: Long): CompletionStage<CurrencyResult> = setBalance(playerId, BigDecimal.valueOf(amount))
    /** Restores the player's balance through the provider reset capability. */
    fun reset(playerId: UUID): CompletionStage<CurrencyResult>
    /** Attempts to atomically transfer positive [amount] between distinct players. */
    fun transfer(from: UUID, to: UUID, amount: BigDecimal): CompletionStage<CurrencyResult>
    /** Long convenience overload for [transfer]. */
    fun transfer(from: UUID, to: UUID, amount: Long): CompletionStage<CurrencyResult> = transfer(from, to, BigDecimal.valueOf(amount))
    /** Formats normalized [amount] using provider formatting or descriptor defaults. */
    fun format(amount: BigDecimal): String
    /** Returns an optional advanced contract registered under exact [type]. */
    fun <T : Any> extension(type: Class<T>): T?
}
