package org.pnfolder.pnlibrary.core.updates.engine

import java.nio.file.Path
import java.time.Instant

/** Post-restart health bookkeeping for staged updates and their rollbacks. */
internal class TransactionHealth(
    private val root: Path,
    private val journals: TransactionJournalRepository,
) {
    /** Commits or fails the transaction at [journalPath] according to [healthy]. */
    fun complete(journalPath: Path, healthy: Boolean): TransactionResult {
        val normalized = journalPath.toAbsolutePath().normalize()
        require(normalized.startsWith(root.toAbsolutePath().normalize())) { "Health journal must stay inside transaction root" }
        val journal = journals.load(normalized)
        require(journal.state == TransactionState.AWAITING_HEALTH || (journal.state == TransactionState.FAILED && journal.healthFailure &&
            !journal.rollbackRequested)) {
            "Transaction ${journal.id} is not awaiting a health check"
        }
        journal.healthCheckedAt = Instant.now().toString()
        journal.healthFailure = !healthy
        journals.transition(normalized, journal, if (healthy) TransactionState.COMMITTED else TransactionState.FAILED)
        return TransactionResult(journal.state, normalized)
    }

    /** Returns transactions waiting for post-restart product-version confirmation. */
    fun awaiting(): List<PendingTransaction> = journals.journals()
        .mapNotNull { path ->
            val journal = runCatching { journals.load(path) }.getOrNull() ?: return@mapNotNull null
            if (journal.state != TransactionState.AWAITING_HEALTH || journal.purpose !in setOf(null, "STAGE")) return@mapNotNull null
            PendingTransaction(path, journal.artifacts.mapNotNull { artifact ->
                artifact.expectedVersion?.let { artifact.component to it }
            }.toMap(), journal.preparedSession, journal.artifacts.filter { it.expectedVersion == null }.map { it.component }.toSet())
        }

    /** Returns requested rollbacks whose restored versions are not yet confirmed. */
    fun awaitingRollback(): List<PendingTransaction> = journals.journals().mapNotNull { path ->
        val journal = journals.load(path)
        if (journal.purpose !in setOf(null, "STAGE") || journal.state != TransactionState.ROLLED_BACK ||
            !journal.rollbackRequested || journal.rollbackHealthVerified) return@mapNotNull null
        PendingTransaction(path, journal.artifacts.mapNotNull { it.previousVersion?.let { version -> it.component to version } }.toMap(),
            journal.rollbackSession, journal.artifacts.filter { it.previousVersion == null }.map { it.component }.toSet(), rollback = true)
    }

    /** Records the post-restart health outcome of the rollback at [path]. */
    fun completeRollback(path: Path, healthy: Boolean) {
        val journal = journals.load(path)
        require(journal.rollbackRequested && (journal.state == TransactionState.ROLLED_BACK || (journal.state == TransactionState.FAILED &&
            journal.healthFailure))) { "No rollback awaits verification" }
        journal.rollbackHealthVerified = true
        journal.healthCheckedAt = Instant.now().toString()
        journal.healthFailure = !healthy
        if (healthy) {
            journal.appliedOperation = "RESTORE"
            journal.state = TransactionState.ROLLED_BACK
        } else journal.state = TransactionState.FAILED
        journals.save(path, journal)
    }

    /** Returns whether the latest health check of every product in [products] succeeded. */
    fun automaticUpdatesHealthy(products: Set<String>): Boolean {
        val latest = mutableMapOf<String, Pair<Instant, Boolean>>()
        journals.journals().forEach { path ->
            val journal = journals.load(path)
            if (journal.purpose !in setOf(null, "STAGE")) return@forEach
            val checkedAt = journal.healthCheckedAt?.let(Instant::parse) ?: return@forEach
            journal.artifacts.filter { it.component in products }.forEach { artifact ->
                val previous = latest[artifact.component]
                if (previous == null || checkedAt > previous.first || (checkedAt == previous.first && journal.healthFailure))
                    latest[artifact.component] = checkedAt to journal.healthFailure
            }
        }
        return latest.values.none { it.second }
    }

    /** Returns saved component sets whose last health check failed. */
    fun failed(): List<PendingTransaction> = journals.journals().mapNotNull { path ->
        val journal = journals.load(path)
        if (journal.purpose !in setOf(null, "STAGE") || journal.state != TransactionState.FAILED || !journal.healthFailure) return@mapNotNull null
        val versions = journal.artifacts.associate { it.component to (if (journal.rollbackRequested) it.previousVersion else it.expectedVersion) }
        PendingTransaction(path, versions.mapNotNull { (product, version) -> version?.let { product to it } }.toMap(),
            unknownProducts = versions.filterValues { it == null }.keys, rollback = journal.rollbackRequested)
    }
}
