package ru.privatenull.pnlibrary.core.updates.engine

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
    fun latest(): Path? = journals.journals()
        .filter { path -> runCatching { isCandidate(journals.load(path)) }.getOrDefault(false) }
        .maxByOrNull { Files.getLastModifiedTime(it).toMillis() }

    /** Resolves exactly one saved transaction; never substitutes the latest one. */
    fun resolve(id: UUID): Path {
        val path = root.toAbsolutePath().normalize().resolve(id.toString()).resolve("journal.json")
        val journal = journals.load(path)
        require(journal.purpose in setOf(null, "STAGE")) { "Internal installation transaction is not a saved component set" }
        require(journal.state in ROLLBACK_STATES) {
            "Transaction $id cannot be rolled back from ${journal.state}"
        }
        require(journals.hasRollbackData(journal)) { "Transaction $id has no complete rollback data" }
        return path
    }

    /** Lists validated rollback candidates, newest first, skipping incomplete or corrupt transactions. */
    fun all(): List<Path> = journals.journals().filter { path ->
        runCatching { isCandidate(journals.load(path)) }.getOrDefault(false)
    }.sortedByDescending { path ->
        journals.load(path).createdAt?.let(Instant::parse) ?: Instant.MIN
    }

    private fun isCandidate(journal: TransactionJournal): Boolean =
        journal.purpose in setOf(null, "STAGE") && journal.state in ROLLBACK_STATES && journals.hasRollbackData(journal)

    private companion object {
        val ROLLBACK_STATES = setOf(TransactionState.COMMITTED, TransactionState.FAILED)
    }
}
