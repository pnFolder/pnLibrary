package ru.privatenull.pnlibrary.core.updates.engine

import com.google.gson.GsonBuilder
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.WRITE
import java.time.Instant

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
 * @property previousVersion version preserved in the backup, when known
 */
data class TransactionArtifact(
    val specification: ArtifactSpecification,
    val source: Path,
    val target: Path,
    val rollbackSource: Path = target,
    val previousVersion: String? = null,
    val previousApi: ApiVersionRange? = null,
    val installedTarget: Path? = null,
)

/** Exact persisted version/API metadata and the digest of its preserved bytes. */
data class JournalVersion(val version: String, val minimumApi: Int, val maximumApi: Int, var sha256: String? = null)

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
 * @property previousVersion version stored in the rollback backup, when known
 */
data class JournalArtifact(
    val component: String,
    val target: String,
    val staged: String,
    val backup: String,
    val targetExisted: Boolean,
    val expectedVersion: String? = null,
    val previousVersion: String? = null,
    val installedTarget: String? = null,
    val expected: JournalVersion? = null,
    val prior: JournalVersion? = null,
    var backupSha256: String? = null,
)

/** Transaction awaiting post-restart health confirmation. */
data class PendingTransaction(
    /** Durable transaction journal path. */
    val journal: Path,
    /** Expected product versions keyed by normalized product ID. */
    val expectedVersions: Map<String, String>,
    val preparedSession: String? = null,
    val unknownProducts: Set<String> = emptySet(),
    val rollback: Boolean = false,
)

/**
 * Durable transaction state used for crash recovery and rollback.
 *
 * @property id unique transaction identifier
 * @property state current durable lifecycle state
 * @property artifacts artifact records participating in this transaction
 * @property createdAt UTC transaction creation time; null for older journals
 * @property planId originating compatibility plan; null for older journals
 * @property targetApi API generation of the original plan; null for older journals
 * @property configurationSnapshot optional immutable configuration snapshot identity
 */
data class TransactionJournal(
    val id: String,
    var state: TransactionState,
    val artifacts: List<JournalArtifact>,
    val createdAt: String? = Instant.now().toString(),
    val planId: String? = null,
    val targetApi: Int? = null,
    val configurationSnapshot: String? = null,
    val purpose: String? = "STAGE",
    val parentTransaction: String? = null,
    val preparedSession: String? = UpdateProcessSession.id,
    var rollbackRequested: Boolean = false,
    var appliedOperation: String? = null,
    var restoreConfigurations: Boolean = false,
    var rollbackSession: String? = null,
    var rollbackHealthVerified: Boolean = false,
    var healthCheckedAt: String? = null,
    var healthFailure: Boolean = false,
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
