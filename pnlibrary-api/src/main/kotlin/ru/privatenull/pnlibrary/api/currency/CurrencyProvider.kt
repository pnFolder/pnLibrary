package ru.privatenull.pnlibrary.api.currency

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletionStage

data class CurrencyAccount(val playerId: UUID)

/** Every provider supplies balance lookup; optional behavior is expressed by capability interfaces. */
interface CurrencyProvider {
    val descriptor: CurrencyDescriptor
    fun balance(account: CurrencyAccount): CompletionStage<BigDecimal>
    fun <T : Any> extension(type: Class<T>): T? = null
}

interface CurrencyDeposits { fun deposit(account: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult> }
interface CurrencyWithdrawals { fun withdraw(account: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult> }
interface CurrencyBalanceMutation { fun setBalance(account: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult> }
interface CurrencyReset { fun reset(account: CurrencyAccount): CompletionStage<CurrencyResult> }
interface CurrencyTransfers { fun transfer(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal): CompletionStage<CurrencyResult> }
interface CurrencyFormatting { fun format(amount: BigDecimal): String }

enum class CurrencyCapability {
    BALANCE, DEPOSIT, WITHDRAW, SET_BALANCE, RESET, TRANSFER, FORMATTING,
}

enum class CurrencyResultStatus { SUCCESS, REJECTED, UNSUPPORTED, UNAVAILABLE, FAILED }
enum class CurrencyRejectReason { INVALID_AMOUNT, INSUFFICIENT_FUNDS, ACCOUNT_NOT_FOUND, LIMIT_EXCEEDED, PROVIDER_REJECTED }

data class CurrencyResult(
    val status: CurrencyResultStatus,
    val previousBalance: BigDecimal? = null,
    val currentBalance: BigDecimal? = null,
    val reason: CurrencyRejectReason? = null,
    val message: String? = null,
    val error: Throwable? = null,
) {
    val isSuccess: Boolean get() = status == CurrencyResultStatus.SUCCESS

    companion object {
        @JvmStatic fun success(previous: BigDecimal? = null, current: BigDecimal? = null) = CurrencyResult(CurrencyResultStatus.SUCCESS, previous, current)
        @JvmStatic fun rejected(reason: CurrencyRejectReason, message: String? = null) = CurrencyResult(CurrencyResultStatus.REJECTED, reason = reason, message = message)
        @JvmStatic fun unsupported(message: String) = CurrencyResult(CurrencyResultStatus.UNSUPPORTED, message = message)
        @JvmStatic fun unavailable(message: String) = CurrencyResult(CurrencyResultStatus.UNAVAILABLE, message = message)
        @JvmStatic fun failed(error: Throwable) = CurrencyResult(CurrencyResultStatus.FAILED, message = error.message, error = error)
    }
}
