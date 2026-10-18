package ru.privatenull.pnlibrary.core.updates.engine

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
        require(purpose in setOf("STAGE", "INSTALL", "RESTORE")) { "invalid transaction purpose" }
        require(targetApi == null || targetApi > 0) { "invalid target API" }
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
                JournalVersion(artifact.specification.version.toString(), artifact.specification.supportedApi.minimum, artifact.specification.supportedApi.maximum),
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
            records.forEach { record ->
                val target = Paths.get(record.target)
                target.parent?.let(Files::createDirectories)
                Files.copy(Paths.get(record.staged), target, StandardCopyOption.REPLACE_EXISTING)
            }
            journals.transition(journalPath, journal, TransactionState.AWAITING_HEALTH)
            return TransactionResult(journal.state, journalPath)
        } catch (error: Throwable) {
            if (activationStarted) {
                runCatching { rollbackInternal(journalPath, journal) }.onFailure(error::addSuppressed)
            } else {
                runCatching { journals.transition(journalPath, journal, TransactionState.FAILED) }.onFailure(error::addSuppressed)
            }
            throw error
        }
    }

    /** Commits or fails the transaction at [journalPath] according to [healthy]. */
    fun completeHealth(journalPath: Path, healthy: Boolean): TransactionResult = health.complete(journalPath, healthy)

    /** Returns transactions waiting for post-restart product-version confirmation. */
    fun awaitingHealth(): List<PendingTransaction> = health.awaiting()

    internal fun awaitingRollbackHealth(): List<PendingTransaction> = health.awaitingRollback()

    internal fun completeRollbackHealth(path: Path, healthy: Boolean) = health.completeRollback(path, healthy)

    internal fun automaticUpdatesHealthy(products: Set<String>): Boolean = health.automaticUpdatesHealthy(products)

    internal fun failedHealthChecks(): List<PendingTransaction> = health.failed()

    /** Recovers an interrupted transaction, rolling back unsafe publication states. */
    fun recover(journalPath: Path): TransactionResult {
        val journal = journals.load(journalPath)
        if ((journal.purpose in setOf("INSTALL", "RESTORE") && journal.state == TransactionState.AWAITING_HEALTH) || journal.state in setOf(
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
    fun rollback(journalPath: Path, restoreConfigurations: Boolean = false): TransactionResult {
        val normalized = journalPath.toAbsolutePath().normalize()
        require(normalized.startsWith(root.toAbsolutePath().normalize())) { "rollback journal must stay inside transaction root" }
        val journal = journals.load(normalized)
        require(journal.state == TransactionState.FAILED || journal.state == TransactionState.COMMITTED) {
            "transaction ${journal.id} cannot be rolled back from ${journal.state}"
        }
        require(journals.hasRollbackData(journal)) { "transaction ${journal.id} has no complete rollback data" }
        require(journal.purpose in setOf(null, "STAGE")) { "internal installation transactions cannot be selected for rollback" }
        journal.rollbackRequested = true
        journal.appliedOperation = null
        require(!restoreConfigurations || journal.configurationSnapshot != null) { "transaction has no configuration snapshot" }
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

    /** Recovers every journal found below the transaction root. */
    fun recoverAll(offline: Boolean = false): List<TransactionResult> {
        if (!Files.isDirectory(root)) return emptyList()
        val paths = journals.journals()
        if (!offline) require(paths.none { path ->
            val journal = journals.load(path)
            (journal.purpose in setOf("INSTALL", "RESTORE") && journal.state == TransactionState.AWAITING_HEALTH) ||
                (journal.state in setOf(TransactionState.PUBLISHING, TransactionState.ACTIVATING, TransactionState.ROLLING_BACK) &&
                (journal.purpose in setOf("INSTALL", "RESTORE") || journal.artifacts.any {
                    Paths.get(it.target).toAbsolutePath().normalize().parent != publicationRoot.resolve("update")
                }))
        }) { "interrupted offline installation must be recovered before starting the server" }
        return paths
            .filter { offline || journals.load(it).purpose in setOf(null, "STAGE") }
            .sorted()
            .map(::recover)
    }

    private fun rollbackInternal(path: Path, journal: TransactionJournal) {
        // Validate the whole set before changing any target.
        journals.validate(path, journal)
        require(journals.hasRollbackData(journal)) { "transaction ${journal.id} has no complete rollback data" }
        journals.transition(path, journal, TransactionState.ROLLING_BACK)
        journal.artifacts.forEach { record ->
            val target = Paths.get(record.target)
            if (record.targetExisted) {
                Files.createDirectories(target.parent)
                Files.copy(Paths.get(record.backup), target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.deleteIfExists(target)
            }
        }
        journals.transition(path, journal, TransactionState.ROLLED_BACK)
    }

    internal fun readJournal(path: Path): TransactionJournal = journals.load(path)

    internal fun pendingInstallations(): List<Path> = journals.journals().filter { path ->
        val journal = journals.load(path)
        journal.purpose in setOf(null, "STAGE") && (
            (journal.state == TransactionState.AWAITING_HEALTH && journal.appliedOperation != "INSTALL") ||
                (journal.state == TransactionState.ROLLED_BACK && journal.rollbackRequested && journal.appliedOperation != "RESTORE")
            )
    }

    internal fun appliedChildJournal(parent: UUID, purpose: String): Path? = journals.journals().firstOrNull { path ->
        val journal = journals.load(path)
        journal.parentTransaction == parent.toString() && journal.purpose == purpose && journal.state == TransactionState.COMMITTED
    }

    internal fun revertInstallation(path: Path) {
        val journal = journals.load(path)
        require(journal.purpose in setOf("INSTALL", "RESTORE")) { "not an internal installation transaction" }
        rollbackInternal(path, journal)
    }

    internal fun markInstalled(path: Path, operation: String) {
        require(operation in setOf("INSTALL", "RESTORE"))
        val journal = journals.load(path)
        journal.appliedOperation = operation
        journals.save(path, journal)
    }
}
