package ru.privatenull.pnlibrary.core.updates.engine

import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.common.io.Digests
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlin.streams.toList

/** Confined, validated access to the transaction journals stored below [root]. */
internal class TransactionJournalRepository(
    private val root: Path,
    private val publicationRoot: Path,
) {
    /** Lists every `journal.json` found in a direct child directory of the root. */
    fun journals(): List<Path> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { directories ->
            directories.filter(Files::isDirectory)
                .map { it.resolve("journal.json") }
                .filter(Files::isRegularFile)
                .toList()
        }
    }

    /** Loads the journal at [path] after checking its location, size, and contents. */
    fun load(path: Path): TransactionJournal {
        val normalized = checkedPath(path, root)
        require(normalized.fileName.toString() == "journal.json" && normalized.parent.parent == root.toAbsolutePath().normalize()) {
            "invalid transaction journal location"
        }
        require(Files.size(normalized) <= MAX_JOURNAL_BYTES) { "transaction journal exceeds size limit" }
        return TransactionJournal.load(normalized).also { validate(normalized, it) }
    }

    /** Rejects journals whose identifiers, versions, digests, or paths are malformed or escape their roots. */
    fun validate(path: Path, journal: TransactionJournal) {
        val normalized = checkedPath(path, root)
        require(journal.id == normalized.parent.fileName.toString()) { "transaction ID does not match its directory" }
        require(journal.artifacts.isNotEmpty()) { "transaction has no artifacts" }
        journal.createdAt?.let(Instant::parse)
        journal.planId?.let(UUID::fromString)
        require(journal.targetApi == null || journal.targetApi > 0) { "invalid target API" }
        journal.configurationSnapshot?.let(UUID::fromString)
        journal.rollbackSession?.let(UUID::fromString)
        journal.healthCheckedAt?.let(Instant::parse)
        require(journal.purpose in setOf(null, "STAGE", "INSTALL", "RESTORE")) { "invalid transaction purpose" }
        journal.parentTransaction?.let(UUID::fromString)
        val targets = journal.artifacts.map { record ->
            checkedPath(Paths.get(record.backup), normalized.parent)
            checkedPath(Paths.get(record.staged), normalized.parent)
            record.installedTarget?.let { checkedPath(Paths.get(it), publicationRoot) }
            require(isDigestOrNull(record.backupSha256)) { "invalid backup digest" }
            listOfNotNull(record.expected, record.prior).forEach { version ->
                SemanticVersion.parse(version.version)
                ApiVersionRange(version.minimumApi, version.maximumApi)
                require(isDigestOrNull(version.sha256)) { "invalid persisted artifact digest" }
            }
            checkedPath(Paths.get(record.target), publicationRoot)
        }
        require(targets.distinct().size == targets.size) { "transaction contains duplicate targets" }
    }

    /** Persists [journal] at [path]. */
    fun save(path: Path, journal: TransactionJournal) = TransactionJournal.save(path, journal)

    /** Moves [journal] to [state] and persists it at [path]. */
    fun transition(path: Path, journal: TransactionJournal, state: TransactionState) {
        journal.state = state
        save(path, journal)
    }

    /** Returns whether every replaced target still has an intact backup. */
    fun hasRollbackData(journal: TransactionJournal): Boolean = journal.artifacts.all { record ->
        !record.targetExisted || (Files.isRegularFile(Paths.get(record.backup)) &&
            ((record.backupSha256 ?: record.prior?.sha256)?.let { digest(Paths.get(record.backup)) == it } ?: true))
    }

    /** Returns the SHA-256 digest of [path]. */
    fun digest(path: Path): String = Digests.sha256(path)

    /** Normalizes [path] and requires it to stay inside [allowedRoot]. */
    fun checkedPath(path: Path, allowedRoot: Path): Path = UpdatePaths.inside(path, allowedRoot)

    private fun isDigestOrNull(value: String?): Boolean = value == null || value.matches(SHA256)

    private companion object {
        const val MAX_JOURNAL_BYTES = 1024 * 1024
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
