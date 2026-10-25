package org.pnfolder.pnlibrary.api.currency

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Immutable ledger record produced for a transaction request.
 *
 * Before/after balances are populated only for accounts involved in the operation.
 * [failure] explains a rejected or failed outcome and must not be used as a stable
 * machine-readable reason.
 *
 * @property id globally unique ledger-record identifier
 * @property currency currency whose balance was addressed
 * @property type mutation requested by the caller
 * @property status persisted outcome of the request
 * @property amount normalized amount used by the operation
 * @property source debited account, when the operation has one
 * @property target credited or updated account, when the operation has one
 * @property actor principal that initiated the request
 * @property service application subsystem responsible for the request
 * @property reason optional human-readable audit explanation
 * @property metadata immutable caller-provided audit attributes
 * @property sourceBalanceBefore source balance immediately before mutation
 * @property sourceBalanceAfter source balance immediately after mutation
 * @property targetBalanceBefore target balance immediately before mutation
 * @property targetBalanceAfter target balance immediately after mutation
 * @property createdAt instant at which storage recorded the outcome
 * @property failure human-readable rejection or infrastructure failure detail
 * @property idempotencyKey retry identity supplied with the original request
 */
data class CurrencyTransaction(
    val id: UUID,
    val currency: CurrencyKey,
    val type: CurrencyTransactionType,
    val status: CurrencyTransactionStatus,
    val amount: BigDecimal,
    val source: CurrencyAccount?,
    val target: CurrencyAccount?,
    val actor: CurrencyActor,
    val service: String,
    val reason: String?,
    val metadata: Map<String, String>,
    val sourceBalanceBefore: BigDecimal? = null,
    val sourceBalanceAfter: BigDecimal? = null,
    val targetBalanceBefore: BigDecimal? = null,
    val targetBalanceAfter: BigDecimal? = null,
    val createdAt: Instant,
    val failure: String? = null,
    val idempotencyKey: String? = null,
)
