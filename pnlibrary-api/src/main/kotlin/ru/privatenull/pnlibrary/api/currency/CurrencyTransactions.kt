package ru.privatenull.pnlibrary.api.currency

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.nio.file.Path
import javax.sql.DataSource

enum class CurrencyTransactionType { CREDIT, DEBIT, TRANSFER, SET_BALANCE, RESET }
enum class CurrencyActorType { PLAYER, PLUGIN, SERVICE, SERVER, SYSTEM }
enum class CurrencyTransactionStatus { COMMITTED, REJECTED, FAILED }

data class CurrencyActor(val type: CurrencyActorType, val id: String) {
    init { require(id.isNotBlank()) { "Currency actor ID must not be blank" } }
    companion object {
        @JvmStatic fun player(id: UUID) = CurrencyActor(CurrencyActorType.PLAYER, id.toString())
        @JvmStatic fun plugin(id: PluginId) = CurrencyActor(CurrencyActorType.PLUGIN, id.value)
        @JvmStatic fun service(id: String) = CurrencyActor(CurrencyActorType.SERVICE, id)
        @JvmStatic fun server() = CurrencyActor(CurrencyActorType.SERVER, "server")
        @JvmStatic fun system(id: String) = CurrencyActor(CurrencyActorType.SYSTEM, id)
    }
}

data class CurrencyTransactionRequest(
    val type: CurrencyTransactionType,
    val amount: BigDecimal,
    val source: CurrencyAccount? = null,
    val target: CurrencyAccount? = null,
    val actor: CurrencyActor,
    val service: String,
    val reason: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val idempotencyKey: String? = null,
) {
    init {
        require(service.isNotBlank()) { "Currency transaction service must not be blank" }
        require(metadata.size <= 64) { "Currency transaction metadata cannot contain more than 64 entries" }
        require(service.length <= 128) { "Currency transaction service is too long" }
        require(reason == null || reason.length <= 512) { "Currency transaction reason is too long" }
        require(idempotencyKey == null || idempotencyKey.isNotBlank() && idempotencyKey.length <= 128) { "Invalid currency idempotency key" }
        metadata.forEach { (key, value) ->
            require(key.isNotBlank() && key.length <= 64 && value.length <= 512) { "Invalid currency transaction metadata" }
        }
    }
}

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

data class CurrencyHistoryQuery(
    val account: UUID? = null,
    val actor: CurrencyActor? = null,
    val service: String? = null,
    val types: Set<CurrencyTransactionType> = emptySet(),
    val from: Instant? = null,
    val until: Instant? = null,
    val offset: Int = 0,
    val limit: Int = 50,
) {
    init {
        require(offset >= 0) { "Currency history offset must not be negative" }
        require(limit in 1..500) { "Currency history limit must be between 1 and 500" }
    }
}

data class CurrencyHistoryPage(
    val items: List<CurrencyTransaction>,
    val offset: Int,
    val hasMore: Boolean,
)

/** Storage must apply a request and append its ledger entry atomically. */
interface CurrencyStorage : AutoCloseable {
    fun balance(currency: CurrencyKey, account: CurrencyAccount): CompletionStage<BigDecimal>
    fun transact(currency: CurrencyKey, descriptor: CurrencyDescriptor, request: CurrencyTransactionRequest): CompletionStage<CurrencyTransaction>
    fun history(currency: CurrencyKey, query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage>
    /** Produces a consistent export of one currency. */
    fun export(currency: CurrencyKey): CompletionStage<CurrencyStorageSnapshot>
    /** Atomically imports balances and ledger rows according to [mode]. */
    fun importSnapshot(snapshot: CurrencyStorageSnapshot, mode: CurrencyImportMode): CompletionStage<CurrencyImportResult>
}

data class CurrencyStorageSnapshot(
    val formatVersion: Int = 1,
    val currency: CurrencyKey,
    val createdAt: Instant,
    val balances: Map<UUID, BigDecimal>,
    val transactions: List<CurrencyTransaction>,
) {
    init { require(formatVersion == 1) { "Unsupported currency snapshot version: $formatVersion" } }
}

enum class CurrencyImportMode {
    /** Deletes the target currency first, then restores the snapshot. */
    REPLACE,
    /** Keeps existing target balances and imports only missing accounts and transactions. */
    MERGE_KEEP_TARGET,
    /** Overwrites balances from the snapshot and merges missing transactions. */
    MERGE_OVERWRITE,
}

data class CurrencyImportResult(
    val currency: CurrencyKey,
    val mode: CurrencyImportMode,
    val importedAccounts: Int,
    val skippedAccounts: Int,
    val importedTransactions: Int,
    val skippedTransactions: Int,
)

data class CurrencyMigrationResult(
    val sourceCurrency: CurrencyKey,
    val targetCurrency: CurrencyKey,
    val import: CurrencyImportResult,
    val startedAt: Instant,
    val completedAt: Instant,
)

/** Advanced managed-currency API available through `currency.extension(CurrencyLedger::class.java)`. */
interface CurrencyLedger {
    fun transact(request: CurrencyTransactionRequest): CompletionStage<CurrencyTransaction>
    fun history(query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage>
}

/** Creates supported persistent storage implementations without exposing core classes. */
interface CurrencyStorageFactory {
    fun file(path: Path): CurrencyStorage
    fun file(path: Path, maximumTransactions: Int): CurrencyStorage
    fun jdbc(dataSource: DataSource): CurrencyStorage
    fun jdbc(dataSource: DataSource, tablePrefix: String): CurrencyStorage
    fun migrate(
        source: CurrencyStorage,
        sourceCurrency: CurrencyKey,
        target: CurrencyStorage,
        targetCurrency: CurrencyKey,
        mode: CurrencyImportMode,
    ): CompletionStage<CurrencyMigrationResult>
}
