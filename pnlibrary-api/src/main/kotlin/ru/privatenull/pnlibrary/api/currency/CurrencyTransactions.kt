package ru.privatenull.pnlibrary.api.currency

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.nio.file.Path
import javax.sql.DataSource

/** Balance mutation represented by a ledger transaction. */
enum class CurrencyTransactionType {
    /** Adds [CurrencyTransaction.amount] to the target account. */ CREDIT,
    /** Subtracts the amount from the source when sufficient funds exist. */ DEBIT,
    /** Atomically subtracts from the source and adds to the target. */ TRANSFER,
    /** Replaces the target balance with the amount. */ SET_BALANCE,
    /** Restores the target account balance to zero. */ RESET,
}

/** Kind of principal responsible for requesting a transaction. */
enum class CurrencyActorType {
    /** A player initiated the operation. */ PLAYER,
    /** A registered plugin initiated the operation. */ PLUGIN,
    /** A named application service initiated the operation. */ SERVICE,
    /** The current Minecraft server instance initiated the operation. */ SERVER,
    /** An internal or automated system process initiated the operation. */ SYSTEM,
}

/** Persisted outcome of one transaction request. */
enum class CurrencyTransactionStatus {
    /** Balance mutation and ledger append completed atomically. */ COMMITTED,
    /** Business validation refused the mutation, but the refusal was recorded. */ REJECTED,
    /** A failed request was recorded without committing its intended mutation. */ FAILED,
}

/**
 * Auditable principal that initiated a currency transaction.
 *
 * @property type category used for filtering and reporting
 * @property id stable nonblank identifier within that category
 */
data class CurrencyActor(val type: CurrencyActorType, val id: String) {
    init { require(id.isNotBlank()) { "Currency actor ID must not be blank" } }
    /** Factories for the supported audited principal categories. */
    companion object {
        /** Creates a player actor from its stable UUID. */
        @JvmStatic fun player(id: UUID) = CurrencyActor(CurrencyActorType.PLAYER, id.toString())
        /** Creates an actor for a registered plugin. */
        @JvmStatic fun plugin(id: PluginId) = CurrencyActor(CurrencyActorType.PLUGIN, id.value)
        /** Creates an actor for a named application service. */
        @JvmStatic fun service(id: String) = CurrencyActor(CurrencyActorType.SERVICE, id)
        /** Creates the canonical current-server actor. */
        @JvmStatic fun server() = CurrencyActor(CurrencyActorType.SERVER, "server")
        /** Creates an internal system actor with a caller-defined identifier. */
        @JvmStatic fun system(id: String) = CurrencyActor(CurrencyActorType.SYSTEM, id)
    }
}

/**
 * Validated intent submitted to [CurrencyStorage.transact].
 *
 * Storage implementations validate the account combination required by [type]. A
 * non-null [idempotencyKey] identifies one logical request within a currency: retries
 * return the already persisted transaction instead of applying the balance change twice.
 *
 * @property type requested balance mutation
 * @property amount amount interpreted according to the currency descriptor
 * @property source debited account for debit and transfer operations
 * @property target credited or replaced account for credit, transfer, set, and reset
 * @property actor principal responsible for the request
 * @property service application operation or subsystem producing the request
 * @property reason optional human-readable audit reason, limited to 512 characters
 * @property metadata structured audit values, limited to 64 entries
 * @property idempotencyKey optional retry identity, nonblank and at most 128 characters
 */
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

/**
 * Filters and paginates reverse-chronological ledger history.
 *
 * All non-null filters are combined with logical AND. [from] is inclusive and [until]
 * is exclusive, making adjacent time ranges safe to concatenate without duplicates.
 * Empty [types] accepts every transaction type.
 *
 * @property account match transactions involving this player as source or target
 * @property actor match the exact initiating principal
 * @property service match the exact application service identifier
 * @property types accepted transaction types, or empty for every type
 * @property from inclusive lower timestamp boundary
 * @property until exclusive upper timestamp boundary
 * @property offset number of matching records to skip
 * @property limit maximum page size, from `1` through `500`
 */
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

/** One offset-based page of reverse-chronological transaction history. */
data class CurrencyHistoryPage(
    /** Transactions in newest-first order. */
    val items: List<CurrencyTransaction>,
    /** Requested zero-based offset in the filtered result. */
    val offset: Int,
    /** Whether another item exists after this page. */
    val hasMore: Boolean,
)

/**
 * Asynchronous persistent balance and ledger boundary.
 *
 * Implementations must commit each balance mutation and its ledger entry atomically.
 * Completion-stage failures represent infrastructure or malformed-storage errors;
 * business rejections are returned as persisted transaction records.
 */
interface CurrencyStorage : AutoCloseable {
    /** Returns the current balance, using zero for an account with no stored row. */
    fun balance(currency: CurrencyKey, account: CurrencyAccount): CompletionStage<BigDecimal>
    /** Atomically applies [request] and appends or returns its ledger record. */
    fun transact(currency: CurrencyKey, descriptor: CurrencyDescriptor, request: CurrencyTransactionRequest): CompletionStage<CurrencyTransaction>
    /** Returns a filtered reverse-chronological history page. */
    fun history(currency: CurrencyKey, query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage>
    /** Produces a consistent export of one currency. */
    fun export(currency: CurrencyKey): CompletionStage<CurrencyStorageSnapshot>
    /** Atomically imports balances and ledger rows according to [mode]. */
    fun importSnapshot(snapshot: CurrencyStorageSnapshot, mode: CurrencyImportMode): CompletionStage<CurrencyImportResult>
}

/**
 * Portable, consistent export of one currency's balances and ledger.
 *
 * @property formatVersion serialization contract version; currently exactly `1`
 * @property currency currency represented by every contained record
 * @property createdAt export creation instant
 * @property balances stored balances keyed by player UUID
 * @property transactions ledger records in storage export order
 */
data class CurrencyStorageSnapshot(
    val formatVersion: Int = 1,
    val currency: CurrencyKey,
    val createdAt: Instant,
    val balances: Map<UUID, BigDecimal>,
    val transactions: List<CurrencyTransaction>,
) {
    init { require(formatVersion == 1) { "Unsupported currency snapshot version: $formatVersion" } }
}

/**
 * Conflict policy used when importing a [CurrencyStorageSnapshot].
 *
 * Use [REPLACE] for recovery into a disposable target. When consolidating two
 * live stores, select a merge mode according to which store owns authoritative
 * balances:
 *
 * ```kotlin
 * target.importSnapshot(snapshot, CurrencyImportMode.MERGE_KEEP_TARGET)
 * ```
 */
enum class CurrencyImportMode {
    /** Deletes all target balances and transactions, then restores the snapshot. */
    REPLACE,
    /** Keeps existing balances and imports only accounts and transactions absent from the target. */
    MERGE_KEEP_TARGET,
    /** Makes snapshot balances authoritative while still merging only missing transactions. */
    MERGE_OVERWRITE,
}

/**
 * Row-level summary of a completed snapshot import.
 *
 * @property currency imported currency identity
 * @property mode conflict policy applied to existing target data
 * @property importedAccounts balance rows inserted or overwritten
 * @property skippedAccounts existing balance rows retained
 * @property importedTransactions ledger rows inserted after duplicate checks
 * @property skippedTransactions ledger rows already identified by transaction ID or idempotency key
 */
data class CurrencyImportResult(
    /** Imported currency. */
    val currency: CurrencyKey,
    /** Conflict policy used for the import. */
    val mode: CurrencyImportMode,
    /** Balance rows inserted or overwritten. */
    val importedAccounts: Int,
    /** Existing balance rows retained. */
    val skippedAccounts: Int,
    /** Ledger rows inserted after duplicate checks. */
    val importedTransactions: Int,
    /** Rows skipped because transaction ID or idempotency key already existed. */
    val skippedTransactions: Int,
)

/** Timing and import result produced by a cross-storage currency migration. */
data class CurrencyMigrationResult(
    /** Currency read from the source storage. */
    val sourceCurrency: CurrencyKey,
    /** Currency written in the target storage. */
    val targetCurrency: CurrencyKey,
    /** Detailed target import counters. */
    val import: CurrencyImportResult,
    /** Instant before export began. */
    val startedAt: Instant,
    /** Instant after target import completed. */
    val completedAt: Instant,
)

/** Advanced managed-currency API available through `currency.extension(CurrencyLedger::class.java)`. */
interface CurrencyLedger {
    /** Applies one managed-currency transaction request. */
    fun transact(request: CurrencyTransactionRequest): CompletionStage<CurrencyTransaction>
    /** Queries the managed currency's persisted ledger. */
    fun history(query: CurrencyHistoryQuery): CompletionStage<CurrencyHistoryPage>
}

/** Creates supported persistent storage implementations without exposing core classes. */
interface CurrencyStorageFactory {
    /** Creates a JSON file storage retaining its default number of ledger rows. */
    fun file(path: Path): CurrencyStorage
    /** Creates a JSON file storage retaining at most [maximumTransactions] ledger rows. */
    fun file(path: Path, maximumTransactions: Int): CurrencyStorage
    /** Creates JDBC storage using the default table prefix. */
    fun jdbc(dataSource: DataSource): CurrencyStorage
    /** Creates JDBC storage using validated alphanumeric [tablePrefix]. */
    fun jdbc(dataSource: DataSource, tablePrefix: String): CurrencyStorage
    /** Consistently exports [sourceCurrency] and atomically imports it as [targetCurrency]. */
    fun migrate(
        source: CurrencyStorage,
        sourceCurrency: CurrencyKey,
        target: CurrencyStorage,
        targetCurrency: CurrencyKey,
        mode: CurrencyImportMode,
    ): CompletionStage<CurrencyMigrationResult>
}
