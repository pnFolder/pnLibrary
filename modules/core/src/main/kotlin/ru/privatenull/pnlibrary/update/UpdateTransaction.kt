package ru.privatenull.pnlibrary.update

import com.google.gson.GsonBuilder
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.Paths
import java.util.UUID
import java.nio.file.StandardOpenOption.WRITE

/** Durable lifecycle state of an update transaction. */
enum class TransactionState {
    CREATED, DOWNLOADING, VERIFIED, STAGED, PUBLISHING, ACTIVATING, AWAITING_HEALTH,
    COMMITTED, ROLLING_BACK, ROLLED_BACK, FAILED,
}

/**
 * One artifact participating in an atomic update transaction.
 *
 * @property specification trusted verification metadata
 * @property source downloaded source file
 * @property target final publication path
 * @property rollbackSource current artifact preserved for rollback
 */
data class TransactionArtifact(
    val specification: ArtifactSpecification,
    val source: Path,
    val target: Path,
    val rollbackSource: Path = target,
)

/**
 * Final observed transaction result.
 *
 * @property state resulting durable lifecycle state
 * @property journal durable transaction journal path
 */
data class TransactionResult(val state: TransactionState, val journal: Path)

/**
 * Durable paths and metadata for one journaled artifact.
 *
 * @property component normalized product identifier
 * @property target publication path
 * @property staged verified staging path
 * @property backup rollback backup path
 * @property targetExisted whether publication replaces an existing file
 * @property expectedVersion version expected during post-restart health verification
 */
data class JournalArtifact(
    val component: String,
    val target: String,
    val staged: String,
    val backup: String,
    val targetExisted: Boolean,
    val expectedVersion: String? = null,
)

/** Transaction awaiting post-restart health confirmation. */
data class PendingTransaction(
    /** Durable transaction journal path. */
    val journal: Path,
    /** Expected product versions keyed by normalized product ID. */
    val expectedVersions: Map<String, String>,
)

/**
 * Durable transaction state used for crash recovery and rollback.
 *
 * @property id unique transaction identifier
 * @property state current durable lifecycle state
 * @property artifacts artifact records participating in this transaction
 */
data class TransactionJournal(
    val id: String,
    var state: TransactionState,
    val artifacts: List<JournalArtifact>,
) {
    /** Loads and persists transaction journals. */
    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()

        /** Loads a transaction journal from [path]. */
        fun load(path: Path): TransactionJournal = Files.newBufferedReader(path, StandardCharsets.UTF_8).use {
            gson.fromJson(it, TransactionJournal::class.java)
        }

        internal fun save(path: Path, journal: TransactionJournal) {
            val parent = path.toAbsolutePath().parent
            Files.createDirectories(parent)
            val temporary = Files.createTempFile(parent, path.fileName.toString(), ".tmp")
            try {
                Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { gson.toJson(journal, it) }
                FileChannel.open(temporary, WRITE).use { it.force(true) }
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}

/** Coordinates verified staging, atomic publication, health confirmation, and rollback. */
class UpdateTransaction(
    private val root: Path,
    private val verifier: ArtifactVerifier,
    publicationRoot: Path = root.toAbsolutePath().normalize().parent,
) {
    private val publicationRoot = publicationRoot.toAbsolutePath().normalize()

    /** Prepares [artifacts], evaluates [healthCheck], and completes the transaction. */
    fun apply(artifacts: List<TransactionArtifact>, healthCheck: () -> Boolean): TransactionResult {
        val prepared = prepareForRestart(artifacts)
        return completeHealth(prepared.journal, healthCheck())
    }

    /** Verifies and publishes [artifacts], leaving the transaction awaiting restart health. */
    fun prepareForRestart(artifacts: List<TransactionArtifact>): TransactionResult {
        require(artifacts.isNotEmpty()) { "transaction must contain at least one artifact" }
        require(artifacts.map { it.specification.product }.distinct().size == artifacts.size) {
            "transaction contains duplicate components"
        }
        require(artifacts.map { it.target.toAbsolutePath().normalize() }.distinct().size == artifacts.size) {
            "transaction contains duplicate targets"
        }
        artifacts.forEach { artifact ->
            val target = artifact.target.toAbsolutePath().normalize()
            require(target.startsWith(publicationRoot)) {
                "publication target must stay inside $publicationRoot"
            }
        }

        val id = UUID.randomUUID().toString()
        val directory = root.toAbsolutePath().normalize().resolve(id)
        val staging = directory.resolve("staging")
        val backups = directory.resolve("backup")
        Files.createDirectories(staging)
        Files.createDirectories(backups)
        val records = artifacts.mapIndexed { index, artifact ->
            JournalArtifact(
                artifact.specification.product.value,
                artifact.target.toAbsolutePath().normalize().toString(),
                staging.resolve("$index-${artifact.target.fileName}").toString(),
                backups.resolve("$index-${artifact.target.fileName}").toString(),
                Files.exists(artifact.rollbackSource),
                artifact.specification.version.toString(),
            )
        }
        val journalPath = directory.resolve("journal.json")
        val journal = TransactionJournal(id, TransactionState.CREATED, records)
        TransactionJournal.save(journalPath, journal)
        var activationStarted = false
        try {
            artifacts.forEach { verifier.verify(it.source, it.specification) }
            transition(journalPath, journal, TransactionState.VERIFIED)
            artifacts.zip(records).forEach { (artifact, record) ->
                Files.copy(artifact.source, Paths.get(record.staged), StandardCopyOption.REPLACE_EXISTING)
            }
            transition(journalPath, journal, TransactionState.STAGED)
            artifacts.zip(records).forEach { (artifact, record) ->
                if (record.targetExisted) Files.copy(
                    artifact.rollbackSource,
                    Paths.get(record.backup),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            transition(journalPath, journal, TransactionState.PUBLISHING)
            activationStarted = true
            records.forEach { record ->
                val target = Paths.get(record.target)
                target.parent?.let(Files::createDirectories)
                Files.copy(Paths.get(record.staged), target, StandardCopyOption.REPLACE_EXISTING)
            }
            transition(journalPath, journal, TransactionState.AWAITING_HEALTH)
            return TransactionResult(journal.state, journalPath)
        } catch (error: Throwable) {
            if (activationStarted) {
                runCatching { rollbackInternal(journalPath, journal) }.onFailure(error::addSuppressed)
            } else {
                runCatching { transition(journalPath, journal, TransactionState.FAILED) }.onFailure(error::addSuppressed)
            }
            throw error
        }
    }

    /** Commits or fails the transaction at [journalPath] according to [healthy]. */
    fun completeHealth(journalPath: Path, healthy: Boolean): TransactionResult {
        val normalized = journalPath.toAbsolutePath().normalize()
        require(normalized.startsWith(root.toAbsolutePath().normalize())) { "health journal must stay inside transaction root" }
        val journal = TransactionJournal.load(normalized)
        require(journal.state == TransactionState.AWAITING_HEALTH) {
            "transaction ${journal.id} is not awaiting a health check"
        }
        transition(normalized, journal, if (healthy) TransactionState.COMMITTED else TransactionState.FAILED)
        return TransactionResult(journal.state, normalized)
    }

    /** Returns transactions waiting for post-restart product-version confirmation. */
    fun awaitingHealth(): List<PendingTransaction> = journals()
        .mapNotNull { path ->
            val journal = runCatching { TransactionJournal.load(path) }.getOrNull() ?: return@mapNotNull null
            if (journal.state != TransactionState.AWAITING_HEALTH) return@mapNotNull null
            PendingTransaction(path, journal.artifacts.mapNotNull { artifact ->
                artifact.expectedVersion?.let { artifact.component to it }
            }.toMap())
        }

    /** Recovers an interrupted transaction, rolling back unsafe publication states. */
    fun recover(journalPath: Path): TransactionResult {
        val journal = TransactionJournal.load(journalPath)
        if (journal.state in setOf(
                TransactionState.PUBLISHING,
                TransactionState.ACTIVATING,
                TransactionState.ROLLING_BACK,
            )
        ) {
            rollbackInternal(journalPath, journal)
        }
        return TransactionResult(journal.state, journalPath)
    }

    /** Restores artifacts recorded by the completed or failed transaction. */
    fun rollback(journalPath: Path): TransactionResult {
        val normalized = journalPath.toAbsolutePath().normalize()
        require(normalized.startsWith(root.toAbsolutePath().normalize())) { "rollback journal must stay inside transaction root" }
        val journal = TransactionJournal.load(normalized)
        require(journal.state == TransactionState.FAILED || journal.state == TransactionState.COMMITTED) {
            "transaction ${journal.id} cannot be rolled back from ${journal.state}"
        }
        require(hasRollbackData(journal)) { "transaction ${journal.id} has no complete rollback data" }
        rollbackInternal(normalized, journal)
        return TransactionResult(journal.state, normalized)
    }

    /** Returns the newest completed transaction that still has rollback data. */
    fun latestRollbackCandidate(): Path? {
        if (!Files.isDirectory(root)) return null
        return Files.list(root).use { directories ->
            directories.filter(Files::isDirectory)
                .map { it.resolve("journal.json") }
                .filter(Files::isRegularFile)
                .filter { path ->
                    runCatching { TransactionJournal.load(path) }.getOrNull()?.let { journal ->
                        journal.state in setOf(TransactionState.FAILED, TransactionState.COMMITTED) && hasRollbackData(journal)
                    } == true
                }
                .max(Comparator.comparingLong { Files.getLastModifiedTime(it).toMillis() })
                .orElse(null)
        }
    }

    /** Recovers every journal found below the transaction root. */
    fun recoverAll(): List<TransactionResult> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { directories ->
            directories
                .filter(Files::isDirectory)
                .map { it.resolve("journal.json") }
                .filter(Files::isRegularFile)
                .sorted()
                .map(::recover)
                .toList()
        }
    }

    private fun rollbackInternal(path: Path, journal: TransactionJournal) {
        transition(path, journal, TransactionState.ROLLING_BACK)
        journal.artifacts.forEach { record ->
            val target = Paths.get(record.target)
            if (record.targetExisted) {
                Files.copy(Paths.get(record.backup), target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.deleteIfExists(target)
            }
        }
        transition(path, journal, TransactionState.ROLLED_BACK)
    }

    private fun hasRollbackData(journal: TransactionJournal): Boolean = journal.artifacts.all { record ->
        !record.targetExisted || Files.isRegularFile(Paths.get(record.backup))
    }

    private fun journals(): List<Path> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { directories ->
            directories.filter(Files::isDirectory)
                .map { it.resolve("journal.json") }
                .filter(Files::isRegularFile)
                .toList()
        }
    }

    private fun transition(path: Path, journal: TransactionJournal, state: TransactionState) {
        journal.state = state
        TransactionJournal.save(path, journal)
    }
}
