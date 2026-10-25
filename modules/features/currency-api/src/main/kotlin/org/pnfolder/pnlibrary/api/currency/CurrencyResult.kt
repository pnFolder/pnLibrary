package org.pnfolder.pnlibrary.api.currency

import java.math.BigDecimal

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
        @JvmOverloads
        fun success(previous: BigDecimal? = null, current: BigDecimal? = null): CurrencyResult =
            CurrencyResult(CurrencyResultStatus.SUCCESS, previous, current)

        /** Creates a business-level rejection that should not normally be retried unchanged. */
        @JvmStatic
        @JvmOverloads
        fun rejected(reason: CurrencyRejectReason, message: String? = null): CurrencyResult =
            CurrencyResult(CurrencyResultStatus.REJECTED, reason = reason, message = message)

        /** Creates a result for a capability the selected provider does not implement. */
        @JvmStatic
        fun unsupported(message: String): CurrencyResult = CurrencyResult(CurrencyResultStatus.UNSUPPORTED, message = message)

        /** Creates a potentially retryable temporary-availability result. */
        @JvmStatic
        fun unavailable(message: String): CurrencyResult = CurrencyResult(CurrencyResultStatus.UNAVAILABLE, message = message)

        /** Creates an unexpected-failure result while preserving the original [error]. */
        @JvmStatic
        fun failed(error: Throwable): CurrencyResult = CurrencyResult(CurrencyResultStatus.FAILED, message = error.message, error = error)
    }
}
