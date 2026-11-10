package org.pnfolder.pnlibrary.core.updates.engine

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Finds saved component sets that can still be rolled back. */
internal class RollbackCatalog(
    private val root: Path,
    private val journals: TransactionJournalRepository,
) {
    /** Returns the most recently modified rollback candidate, if any. */
    fun latest(): Path? = candidates().map { it.first }.maxByOrNull { Files.getLastModifiedTime(it).toMillis() }

    /** Resolves exactly one saved transaction; never substitutes the latest one. */
    fun resolve(id: UUID): Path {
        val path = root.toAbsolutePath().normalize().resolve(id.toString()).resolve("journal.json")
        val journal = journals.load(path)
        require(journal.purpose in setOf(null, "STAGE")) { "Internal installation transaction is not a saved component set" }
        require(journal.isRestorable()) { "Transaction $id cannot be rolled back from ${journal.state}" }
        require(journals.hasRollbackData(journal)) { "Transaction $id has no complete rollback data" }
        return path
    }

    /** Lists validated rollback candidates, newest first, skipping incomplete or corrupt transactions. */
    fun all(): List<Path> = candidates()
        .sortedByDescending { (_, journal) -> journal.createdAt?.let(Instant::parse) ?: Instant.MIN }
        .map { it.first }

    /** An unreadable journal is reported by the repository and skipped, so it cannot hide the other sets. */
    private fun candidates() = journals.loadAll().filter { (path, journal) ->
        journal.purpose in setOf(null, "STAGE") && journal.isRestorable() && journals.hasReadableRollbackData(path, journal)
    }
}

/**
 * Whether the set reached the disk and its backups can be restored: it committed, or the restarted server failed
 * its health check. A set that failed before publication changed nothing, and "restoring" it would put back files
 * that may be older than what is installed now, or delete a component installed later.
 */
internal fun TransactionJournal.isRestorable(): Boolean =
    state == TransactionState.COMMITTED || (state == TransactionState.FAILED && healthFailure)
