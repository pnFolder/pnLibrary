package ru.privatenull.pnlibrary.api.currency

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletionStage

/** Currency account identified by a stable player UUID. */
data class CurrencyAccount(
    /** Player that owns the balance. */
    val playerId: UUID,
)

/**
 * Minimum contract implemented by every currency backend.
 *
 * Balance lookup is mandatory. Mutating and presentation operations are exposed through
 * optional extension interfaces such as [CurrencyDeposits] and [CurrencyFormatting].
 */
interface CurrencyProvider {
    /** Human-readable metadata and decimal rules for this currency. */
    val descriptor: CurrencyDescriptor
    /** Asynchronously returns the current normalized balance of [account]. */
    fun balance(account: CurrencyAccount): CompletionStage<BigDecimal>
    /** Returns an optional provider-specific or standard capability of exact [type]. */
    fun <T : Any> extension(type: Class<T>): T? = null
}

/** Optional capability for increasing an account balance. */
interface CurrencyDeposits {
    /** Attempts to add positive [amount] to [account]. */
    fun deposit(account: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult>
}

/** Optional capability for decreasing an account balance. */
interface CurrencyWithdrawals {
    /** Attempts to subtract positive [amount] from [account]. */
    fun withdraw(account: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult>
}

/** Optional capability for replacing an account balance. */
interface CurrencyBalanceMutation {
    /** Attempts to replace the balance of [account] with [amount]. */
    fun setBalance(account: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult>
}

/** Optional capability for restoring an account to the provider-defined default balance. */
interface CurrencyReset {
    /** Resets [account] and reports the previous and resulting balances when available. */
    fun reset(account: CurrencyAccount): CompletionStage<CurrencyResult>
}

/** Optional capability for atomically moving value between two accounts. */
interface CurrencyTransfers {
    /** Attempts one indivisible transfer of positive [amount] from [from] to [to]. */
    fun transfer(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult>
}

/** Optional provider-specific presentation of normalized currency amounts. */
interface CurrencyFormatting {
    /** Formats [amount] for display without changing any balance. */
    fun format(amount: BigDecimal): String
}

/** Operations that a [CurrencyProvider] may expose beyond mandatory balance lookup. */
enum class CurrencyCapability {
    /** Mandatory balance lookup. */ BALANCE,
    /** [CurrencyDeposits] support. */ DEPOSIT,
    /** [CurrencyWithdrawals] support. */ WITHDRAW,
    /** [CurrencyBalanceMutation] support. */ SET_BALANCE,
    /** [CurrencyReset] support. */ RESET,
    /** [CurrencyTransfers] support. */ TRANSFER,
    /** [CurrencyFormatting] support. */ FORMATTING,
}

/** Outcome category of a currency mutation. */
enum class CurrencyResultStatus {
    /** Mutation committed successfully. */ SUCCESS,
    /** Valid operation was refused for a known business reason. */ REJECTED,
    /** Provider does not implement the requested capability. */ UNSUPPORTED,
    /** Capability exists but its backend is temporarily unavailable. */ UNAVAILABLE,
    /** Unexpected implementation or infrastructure failure. */ FAILED,
}

/** Stable business reason accompanying [CurrencyResultStatus.REJECTED]. */
enum class CurrencyRejectReason {
    /** Amount is negative, zero where forbidden, or violates decimal rules. */ INVALID_AMOUNT,
    /** Source account cannot cover the requested debit. */ INSUFFICIENT_FUNDS,
    /** Target account does not exist and cannot be created. */ ACCOUNT_NOT_FOUND,
    /** Provider-defined account or transaction limit would be exceeded. */ LIMIT_EXCEEDED,
    /** Provider rejected the request without a more specific standard reason. */ PROVIDER_REJECTED,
}

/**
 * Structured result returned by currency mutation capabilities.
 *
 * @property status high-level outcome used for program flow
 * @property previousBalance balance before a committed operation, when available
 * @property currentBalance balance after a committed operation, when available
 * @property reason stable reason for a rejected operation
 * @property message optional diagnostic or user-displayable detail supplied by the provider
 * @property error original unexpected failure; normally present only for [CurrencyResultStatus.FAILED]
 */
data class CurrencyResult(
    val status: CurrencyResultStatus,
    val previousBalance: BigDecimal? = null,
    val currentBalance: BigDecimal? = null,
    val reason: CurrencyRejectReason? = null,
    val message: String? = null,
    val error: Throwable? = null,
) {
    /** Whether the requested mutation committed successfully. */
    val isSuccess: Boolean get() = status == CurrencyResultStatus.SUCCESS

    /** Structured factories for successful, rejected, unsupported, and failed outcomes. */
    companion object {
        /** Creates a successful result with optional before-and-after balances. */
        @JvmStatic
        fun success(previous: BigDecimal? = null, current: BigDecimal? = null) =
            CurrencyResult(CurrencyResultStatus.SUCCESS, previous, current)

        /** Creates a business-level rejection that should not normally be retried unchanged. */
        @JvmStatic
        fun rejected(reason: CurrencyRejectReason, message: String? = null) =
            CurrencyResult(CurrencyResultStatus.REJECTED, reason = reason, message = message)

        /** Creates a result for a capability the selected provider does not implement. */
        @JvmStatic
        fun unsupported(message: String) = CurrencyResult(CurrencyResultStatus.UNSUPPORTED, message = message)

        /** Creates a potentially retryable temporary-availability result. */
        @JvmStatic
        fun unavailable(message: String) = CurrencyResult(CurrencyResultStatus.UNAVAILABLE, message = message)

        /** Creates an unexpected-failure result while preserving the original [error]. */
        @JvmStatic
        fun failed(error: Throwable) = CurrencyResult(CurrencyResultStatus.FAILED, message = error.message, error = error)
    }
}
