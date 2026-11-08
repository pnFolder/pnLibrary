package org.pnfolder.pnlibrary.core.updates.engine

import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Coordinates verified staging, atomic publication, health confirmation, and rollback. */
internal class UpdateTransaction(
    private val root: Path,
    private val verifier: ArtifactVerifier,
    publicationRoot: Path = root.toAbsolutePath().normalize().parent,
) {
    private val publicationRoot = publicationRoot.toAbsolutePath().normalize()
    private val journals = TransactionJournalRepository(root, this.publicationRoot)
    private val health = TransactionHealth(root, journals)
    private val rollbacks = RollbackCatalog(root, journals)

    /** Prepares [artifacts], evaluates [healthCheck], and completes the transaction. */
    fun apply(artifacts: List<TransactionArtifact>, healthCheck: () -> Boolean): TransactionResult {
        val prepared = prepareForRestart(artifacts)
        return completeHealth(prepared.journal, healthCheck())
    }

    /** Verifies and publishes [artifacts], leaving the transaction awaiting restart health. */
    fun prepareForRestart(artifacts: List<TransactionArtifact>, planId: UUID? = null, targetApi: Int? = null, configurationSnapshot: UUID? = null,
        purpose: String = "STAGE", parentTransaction: UUID? = null): TransactionResult {
        require(purpose in setOf("STAGE", "INSTALL", "RESTORE")) { "Invalid transaction purpose" }
        require(targetApi == null || targetApi > 0) { "Invalid target API" }
        require(artifacts.isNotEmpty()) { "Transaction must contain at least one artifact" }
        require(artifacts.map { it.specification.product }.distinct().size == artifacts.size) {
            "Transaction contains duplicate components"
        }
        require(artifacts.map { it.target.toAbsolutePath().normalize() }.distinct().size == artifacts.size) {
            "Transaction contains duplicate targets"
        }
        artifacts.forEach { artifact ->
            val target = artifact.target.toAbsolutePath().normalize()
            require(target.startsWith(publicationRoot)) {
                "Publication target must stay inside $publicationRoot"
            }
            journals.checkedPath(target, publicationRoot)
        }

        val id = UUID.randomUUID().toString()
        val directory = root.toAbsolutePath().normalize().resolve(id)
        journals.checkedPath(directory.resolve("journal.json"), root)
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
                artifact.previousVersion,
                artifact.installedTarget?.toAbsolutePath()?.normalize()?.toString(),
                JournalVersion(artifact.specification.version.toString(), artifact.specification.supportedApi.minimum,
                    artifact.specification.supportedApi.maximum),
                artifact.previousApi?.let { api -> artifact.previousVersion?.let { JournalVersion(it, api.minimum, api.maximum) } },
            )
        }
        val journalPath = directory.resolve("journal.json")
        val journal = TransactionJournal(id, TransactionState.CREATED, records, planId = planId?.toString(), targetApi = targetApi,
            configurationSnapshot = configurationSnapshot?.toString(), purpose = purpose, parentTransaction = parentTransaction?.toString())
        journals.save(journalPath, journal)
        var activationStarted = false
        try {
            artifacts.forEach { verifier.verify(it.source, it.specification) }
            journals.transition(journalPath, journal, TransactionState.VERIFIED)
            artifacts.zip(records).forEach { (artifact, record) ->
                Files.copy(artifact.source, Paths.get(record.staged), StandardCopyOption.REPLACE_EXISTING)
                verifier.verify(Paths.get(record.staged), artifact.specification.copy(fileName = Paths.get(record.staged).fileName.toString()))
                record.expected?.sha256 = journals.digest(Paths.get(record.staged))
            }
            journals.transition(journalPath, journal, TransactionState.STAGED)
            artifacts.zip(records).forEach { (artifact, record) ->
                if (record.targetExisted) {
                    Files.copy(artifact.rollbackSource, Paths.get(record.backup), StandardCopyOption.REPLACE_EXISTING)
                    record.backupSha256 = journals.digest(Paths.get(record.backup))
                    record.prior?.sha256 = record.backupSha256
                }
            }
            journals.transition(journalPath, journal, TransactionState.PUBLISHING)
            activationStarted = true
            records.forEach { record -> replaceAtomically(Paths.get(record.staged), Paths.get(record.target)) }
            journals.transition(journalPath, journal, TransactionState.AWAITING_HEALTH)
            return TransactionResult(journal.state, journalPath)
        } catch (error: Throwable) {
            if (activationStarted) {
                runCatchingNonFatal { rollbackInternal(journalPath, journal) }.onFailure(error::addSuppressed)
            } else {
                runCatchingNonFatal { journals.transition(journalPath, journal, TransactionState.FAILED) }.onFailure(error::addSuppressed)
            }
            throw error
        }
    }

    /** Commits or fails the transaction at [journalPath] according to [healthy]. */
    fun completeHealth(journalPath: Path, healthy: Boolean): TransactionResult = health.complete(journalPath, healthy)

    /** Returns transactions waiting for post-restart product-version confirmation. */
    fun awaitingHealth(): List<PendingTransaction> = health.awaiting()

    /** Rollbacks that were applied and still wait for a health check. */
    internal fun awaitingRollbackHealth(): List<PendingTransaction> = health.awaitingRollback()

    /** Records the health result of the rollback journal at [path]. */
    internal fun completeRollbackHealth(path: Path, healthy: Boolean) = health.completeRollback(path, healthy)

    /** Whether no unhealthy update transaction involves [products]. */
    internal fun automaticUpdatesHealthy(products: Set<String>): Boolean = health.automaticUpdatesHealthy(products)

    /** Transactions whose health check failed. */
    internal fun failedHealthChecks(): List<PendingTransaction> = health.failed()

    /** Recovers an interrupted transaction, rolling back unsafe publication states. */
    fun recover(journalPath: Path): TransactionResult {
        val journal = journals.load(journalPath)
        if (interruptedInstallation(journal) || journal.state in UNSAFE_STATES) rollbackInternal(journalPath, journal)
        return TransactionResult(journal.state, journalPath)
    }

    /** Restores artifacts recorded by the completed or failed transaction. */
    fun rollback(journalPath: Path, restoreConfigurations: Boolean = false): TransactionResult {
        val normalized = journalPath.toAbsolutePath().normalize()
        require(normalized.startsWith(root.toAbsolutePath().normalize())) { "Rollback journal must stay inside transaction root" }
        val journal = journals.load(normalized)
        require(journal.isRestorable()) { "Transaction ${journal.id} cannot be rolled back from ${journal.state}" }
        require(journals.hasRollbackData(journal)) { "Transaction ${journal.id} has no complete rollback data" }
        require(journal.purpose in SAVED_SET_PURPOSES) { "Internal installation transactions cannot be selected for rollback" }
        require(!restoreConfigurations || journal.configurationSnapshot != null) { "Transaction has no configuration snapshot" }
        journal.rollbackRequested = true
        journal.appliedOperation = null
        journal.restoreConfigurations = restoreConfigurations
        journal.rollbackSession = UpdateProcessSession.id
        journal.rollbackHealthVerified = false
        rollbackInternal(normalized, journal)
        return TransactionResult(journal.state, normalized)
    }

    /** Returns the newest completed transaction that still has rollback data. */
    fun latestRollbackCandidate(): Path? = rollbacks.latest()

    /** Resolves exactly one saved transaction; never substitutes the latest one. */
    fun rollbackCandidate(id: UUID): Path = rollbacks.resolve(id)

    /** Lists validated saved rollback sets without exposing incomplete or corrupt transactions. */
    fun rollbackCandidates(): List<Path> = rollbacks.all()

    /**
     * Recovers the saved sets at server start. Refuses to start while a set that touched the loaded plugin JARs is
     * interrupted: only the offline installer may repair those, because the server may already have loaded them.
     */
    fun recoverAll(): List<TransactionResult> {
        if (!Files.isDirectory(root)) return emptyList()
        val paths = journals.journals()
        require(paths.none { blocksServerStart(journals.load(it)) }) {
            "interrupted offline installation must be recovered before starting the server"
        }
        return paths.filter { journals.load(it).purpose in SAVED_SET_PURPOSES }.sorted().map(::recover)
    }

    /** Recovers every journal, internal installations included; called only by the offline installer. */
    fun recoverAllOffline(): List<TransactionResult> {
        if (!Files.isDirectory(root)) return emptyList()
        return journals.journals().sorted().map(::recover)
    }

    private fun interruptedInstallation(journal: TransactionJournal): Boolean =
        journal.purpose in INTERNAL_PURPOSES && journal.state == TransactionState.AWAITING_HEALTH

    private fun blocksServerStart(journal: TransactionJournal): Boolean {
        if (interruptedInstallation(journal)) return true
        if (journal.state !in UNSAFE_STATES) return false
        return journal.purpose in INTERNAL_PURPOSES || journal.artifacts.any {
            Paths.get(it.target).toAbsolutePath().normalize().parent != publicationRoot.resolve("update")
        }
    }

    private fun rollbackInternal(path: Path, journal: TransactionJournal) {
        // Validate the whole set before changing any target.
        journals.validate(path, journal)
        require(journals.hasRollbackData(journal)) { "Transaction ${journal.id} has no complete rollback data" }
        journals.transition(path, journal, TransactionState.ROLLING_BACK)
        journal.artifacts.forEach { record ->
            val target = Paths.get(record.target)
            if (record.targetExisted) {
                replaceAtomically(Paths.get(record.backup), target)
            } else {
                retryWhileLocked { Files.deleteIfExists(target) }
                // A crash during publication can leave the copy that was about to be swapped in.
                Files.deleteIfExists(publishingCopy(target))
            }
        }
        journals.transition(path, journal, TransactionState.ROLLED_BACK)
    }

    /** Copies [source] beside [target] first and then swaps it in, so a crash never leaves a half-written jar. */
    private fun replaceAtomically(source: Path, target: Path) {
        target.parent?.let(Files::createDirectories)
        val publishing = publishingCopy(target)
        try {
            Files.copy(source, publishing, StandardCopyOption.REPLACE_EXISTING)
            retryWhileLocked { AtomicFiles.move(publishing, target) }
        } catch (error: Throwable) {
            runCatchingNonFatal { Files.deleteIfExists(publishing) }.onFailure(error::addSuppressed)
            throw error
        }
    }

    private fun publishingCopy(target: Path): Path = target.resolveSibling("${target.fileName}.publishing")

    /** Loads the journal at [path]. */
    internal fun readJournal(path: Path): TransactionJournal = journals.load(path)

    /** Saved-set journals that were applied but not yet installed or restored. */
    internal fun pendingInstallations(): List<Path> = journals.journals().filter { path ->
        val journal = journals.load(path)
        journal.purpose in SAVED_SET_PURPOSES && (
            (journal.state == TransactionState.AWAITING_HEALTH && journal.appliedOperation != "INSTALL") ||
                (journal.state == TransactionState.ROLLED_BACK && journal.rollbackRequested && journal.appliedOperation != "RESTORE")
            )
    }

    /** The committed journal of [purpose] that belongs to [parent], if any. */
    internal fun appliedChildJournal(parent: UUID, purpose: String): Path? = journals.journals().firstOrNull { path ->
        val journal = journals.load(path)
        journal.parentTransaction == parent.toString() && journal.purpose == purpose && journal.state == TransactionState.COMMITTED
    }

    /** Rolls back an internal installation transaction. */
    internal fun revertInstallation(path: Path) {
        val journal = journals.load(path)
        require(journal.purpose in INTERNAL_PURPOSES) { "Not an internal installation transaction" }
        rollbackInternal(path, journal)
    }

    /** Remembers that [operation] was applied for the journal at [path]. */
    internal fun markInstalled(path: Path, operation: String) {
        require(operation in INTERNAL_PURPOSES)
        val journal = journals.load(path)
        journal.appliedOperation = operation
        journals.save(path, journal)
    }

    /**
     * Runs [action], trying again a few times when the file system refuses it. On Windows a jar that antivirus or the
     * previous JVM still holds open cannot be replaced for a moment, and failing the whole transaction for that is
     * worse than waiting a second.
     */
    private fun retryWhileLocked(action: () -> Unit) {
        var attempt = 1
        while (true) {
            try {
                action()
                return
            } catch (error: FileSystemException) {
                if (attempt >= LOCK_ATTEMPTS) throw error
                attempt++
                Thread.sleep(LOCK_RETRY_MILLIS)
            }
        }
    }

    private companion object {
        const val LOCK_ATTEMPTS = 5
        const val LOCK_RETRY_MILLIS = 200L
        val SAVED_SET_PURPOSES = setOf(null, "STAGE")
        val INTERNAL_PURPOSES = setOf("INSTALL", "RESTORE")
        val UNSAFE_STATES = setOf(TransactionState.PUBLISHING, TransactionState.ACTIVATING, TransactionState.ROLLING_BACK)
    }
}
