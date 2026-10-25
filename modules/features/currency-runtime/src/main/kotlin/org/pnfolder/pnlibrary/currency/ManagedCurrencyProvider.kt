package org.pnfolder.pnlibrary.currency

import org.pnfolder.pnlibrary.api.currency.CurrencyAccount
import org.pnfolder.pnlibrary.api.currency.CurrencyActor
import org.pnfolder.pnlibrary.api.currency.CurrencyBalanceMutation
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings
import org.pnfolder.pnlibrary.api.currency.CurrencyDeposits
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery
import org.pnfolder.pnlibrary.api.currency.CurrencyKey
import org.pnfolder.pnlibrary.api.currency.CurrencyLedger
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider
import org.pnfolder.pnlibrary.api.currency.CurrencyRejectReason
import org.pnfolder.pnlibrary.api.currency.CurrencyReset
import org.pnfolder.pnlibrary.api.currency.CurrencyResult
import org.pnfolder.pnlibrary.api.currency.CurrencyStorage
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType
import org.pnfolder.pnlibrary.api.currency.CurrencyTransfers
import org.pnfolder.pnlibrary.api.currency.CurrencyWithdrawals
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Storage-backed provider that validates requests before crossing the persistence boundary. */
internal class ManagedCurrencyProvider(
    private val key: CurrencyKey,
    override val descriptor: CurrencyDescriptor,
    private val storage: CurrencyStorage,
    private val service: String,
    private val ownsStorage: Boolean,
    private val commandSettings: CurrencyCommandSettings,
) : CurrencyProvider, CurrencyDeposits, CurrencyWithdrawals, CurrencyBalanceMutation,
    CurrencyReset, CurrencyTransfers, CurrencyLedger, AutoCloseable {

    override fun balance(account: CurrencyAccount) = storage.balance(key, account)

    override fun deposit(account: CurrencyAccount, amount: BigDecimal) = mutate(
        type = CurrencyTransactionType.CREDIT,
        amount = amount,
        target = account,
        reason = "API credit",
    )

    override fun withdraw(account: CurrencyAccount, amount: BigDecimal) = mutate(
        type = CurrencyTransactionType.DEBIT,
        amount = amount,
        source = account,
        reason = "API debit",
    )

    override fun setBalance(account: CurrencyAccount, amount: BigDecimal) = mutate(
        type = CurrencyTransactionType.SET_BALANCE,
        amount = amount,
        target = account,
        reason = "API balance update",
    )

    override fun reset(account: CurrencyAccount) = mutate(
        type = CurrencyTransactionType.RESET,
        amount = BigDecimal.ZERO,
        target = account,
        reason = "API balance reset",
    )

    override fun transfer(from: CurrencyAccount, to: CurrencyAccount, amount: BigDecimal) = mutate(
        type = CurrencyTransactionType.TRANSFER,
        amount = amount,
        source = from,
        target = to,
        reason = "API transfer",
    )

    override fun transact(request: CurrencyTransactionRequest): CompletionStage<CurrencyTransaction> {
        val failure = CurrencyRequestValidation.failure(descriptor, request)
        if (failure == null) return storage.transact(key, descriptor, request)
        return CompletableFuture.completedFuture(rejectedTransaction(request, failure))
    }

    override fun history(query: CurrencyHistoryQuery) = storage.history(key, query)

    override fun <T : Any> extension(type: Class<T>): T? = when {
        type.isInstance(this) -> type.cast(this)
        type.isInstance(commandSettings) -> type.cast(commandSettings)
        else -> null
    }

    override fun close() {
        if (ownsStorage) storage.close()
    }

    private fun rejectedTransaction(request: CurrencyTransactionRequest, failure: String) = CurrencyTransaction(
        id = UUID.randomUUID(),
        currency = key,
        type = request.type,
        status = CurrencyTransactionStatus.REJECTED,
        amount = request.amount,
        source = request.source,
        target = request.target,
        actor = request.actor,
        service = request.service,
        reason = request.reason,
        metadata = request.metadata,
        createdAt = Instant.now(),
        failure = failure,
        idempotencyKey = request.idempotencyKey,
    )

    private fun mutate(
        type: CurrencyTransactionType,
        amount: BigDecimal,
        source: CurrencyAccount? = null,
        target: CurrencyAccount? = null,
        reason: String,
    ): CompletionStage<CurrencyResult> = transact(
        CurrencyTransactionRequest(
            type = type,
            amount = amount,
            source = source,
            target = target,
            actor = CurrencyActor.system("currency-api"),
            service = service,
            reason = reason,
        ),
    ).thenApply(::toResult)

    private fun toResult(transaction: CurrencyTransaction): CurrencyResult = when (transaction.status) {
        CurrencyTransactionStatus.COMMITTED -> CurrencyResult.success(
            transaction.sourceBalanceBefore ?: transaction.targetBalanceBefore,
            transaction.sourceBalanceAfter ?: transaction.targetBalanceAfter,
        )
        CurrencyTransactionStatus.REJECTED ->
            CurrencyResult.rejected(CurrencyRejectReason.PROVIDER_REJECTED, transaction.failure)
        CurrencyTransactionStatus.FAILED ->
            CurrencyResult.failed(IllegalStateException(transaction.failure ?: "Currency transaction failed"))
    }
}
