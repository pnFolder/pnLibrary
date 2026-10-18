package ru.privatenull.pnlibrary.core.updates.engine

import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.common.io.Digests
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import kotlin.streams.toList

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
            checkedPath(target, publicationRoot)
        }

        val id = UUID.randomUUID().toString()
        val directory = root.toAbsolutePath().normalize().resolve(id)
        checkedPath(directory.resolve("journal.json"), root)
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
        TransactionJournal.save(journalPath, journal)
        var activationStarted = false
        try {
            artifacts.forEach { verifier.verify(it.source, it.specification) }
            transition(journalPath, journal, TransactionState.VERIFIED)
            artifacts.zip(records).forEach { (artifact, record) ->
                Files.copy(artifact.source, Paths.get(record.staged), StandardCopyOption.REPLACE_EXISTING)
                verifier.verify(Paths.get(record.staged), artifact.specification.copy(fileName = Paths.get(record.staged).fileName.toString()))
                record.expected?.sha256 = digest(Paths.get(record.staged))
            }
            transition(journalPath, journal, TransactionState.STAGED)
            artifacts.zip(records).forEach { (artifact, record) ->
                if (record.targetExisted) {
                    Files.copy(artifact.rollbackSource, Paths.get(record.backup), StandardCopyOption.REPLACE_EXISTING)
                    record.backupSha256 = digest(Paths.get(record.backup))
                    record.prior?.sha256 = record.backupSha256
                }
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
        val journal = loadChecked(normalized)
        require(journal.state == TransactionState.AWAITING_HEALTH || (journal.state == TransactionState.FAILED && journal.healthFailure && !journal.rollbackRequested)) {
            "transaction ${journal.id} is not awaiting a health check"
        }
        journal.healthCheckedAt = Instant.now().toString()
        journal.healthFailure = !healthy
        transition(normalized, journal, if (healthy) TransactionState.COMMITTED else TransactionState.FAILED)
        return TransactionResult(journal.state, normalized)
    }

    /** Returns transactions waiting for post-restart product-version confirmation. */
    fun awaitingHealth(): List<PendingTransaction> = journals()
        .mapNotNull { path ->
            val journal = runCatching { loadChecked(path) }.getOrNull() ?: return@mapNotNull null
            if (journal.state != TransactionState.AWAITING_HEALTH || journal.purpose !in setOf(null, "STAGE")) return@mapNotNull null
            PendingTransaction(path, journal.artifacts.mapNotNull { artifact ->
                artifact.expectedVersion?.let { artifact.component to it }
            }.toMap(), journal.preparedSession, journal.artifacts.filter { it.expectedVersion == null }.map { it.component }.toSet())
        }

    internal fun awaitingRollbackHealth(): List<PendingTransaction> = journals().mapNotNull { path ->
        val journal = loadChecked(path)
        if (journal.purpose !in setOf(null, "STAGE") || journal.state != TransactionState.ROLLED_BACK ||
            !journal.rollbackRequested || journal.rollbackHealthVerified) return@mapNotNull null
        PendingTransaction(path, journal.artifacts.mapNotNull { it.previousVersion?.let { version -> it.component to version } }.toMap(),
            journal.rollbackSession, journal.artifacts.filter { it.previousVersion == null }.map { it.component }.toSet(), rollback = true)
    }

    internal fun completeRollbackHealth(path: Path, healthy: Boolean) {
        val journal = loadChecked(path)
        require(journal.rollbackRequested && (journal.state == TransactionState.ROLLED_BACK || (journal.state == TransactionState.FAILED && journal.healthFailure))) { "no rollback awaits verification" }
        journal.rollbackHealthVerified = true
        journal.healthCheckedAt = Instant.now().toString()
        journal.healthFailure = !healthy
        if (healthy) {
            journal.appliedOperation = "RESTORE"
            journal.state = TransactionState.ROLLED_BACK
        } else journal.state = TransactionState.FAILED
        TransactionJournal.save(path, journal)
    }

    internal fun automaticUpdatesHealthy(products: Set<String>): Boolean {
        val latest = mutableMapOf<String, Pair<Instant, Boolean>>()
        journals().forEach { path ->
            val journal = loadChecked(path)
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

    internal fun failedHealthChecks(): List<PendingTransaction> = journals().mapNotNull { path ->
        val journal = loadChecked(path)
        if (journal.purpose !in setOf(null, "STAGE") || journal.state != TransactionState.FAILED || !journal.healthFailure) return@mapNotNull null
        val versions = journal.artifacts.associate { it.component to (if (journal.rollbackRequested) it.previousVersion else it.expectedVersion) }
        PendingTransaction(path, versions.mapNotNull { (product, version) -> version?.let { product to it } }.toMap(),
            unknownProducts = versions.filterValues { it == null }.keys, rollback = journal.rollbackRequested)
    }

    /** Recovers an interrupted transaction, rolling back unsafe publication states. */
    fun recover(journalPath: Path): TransactionResult {
        val journal = loadChecked(journalPath)
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
        val journal = loadChecked(normalized)
        require(journal.state == TransactionState.FAILED || journal.state == TransactionState.COMMITTED) {
            "transaction ${journal.id} cannot be rolled back from ${journal.state}"
        }
        require(hasRollbackData(journal)) { "transaction ${journal.id} has no complete rollback data" }
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
    fun latestRollbackCandidate(): Path? {
        if (!Files.isDirectory(root)) return null
        return Files.list(root).use { directories ->
            directories.filter(Files::isDirectory)
                .map { it.resolve("journal.json") }
                .filter(Files::isRegularFile)
                .filter { path ->
                    runCatching { loadChecked(path) }.getOrNull()?.let { journal ->
                        journal.purpose in setOf(null, "STAGE") && journal.state in setOf(TransactionState.FAILED, TransactionState.COMMITTED) && hasRollbackData(journal)
                    } == true
                }
                .max(Comparator.comparingLong { Files.getLastModifiedTime(it).toMillis() })
                .orElse(null)
        }
    }

    /** Resolves exactly one saved transaction; never substitutes the latest one. */
    fun rollbackCandidate(id: UUID): Path {
        val path = root.toAbsolutePath().normalize().resolve(id.toString()).resolve("journal.json")
        val journal = loadChecked(path)
        require(journal.purpose in setOf(null, "STAGE")) { "internal installation transaction is not a saved component set" }
        require(journal.state in setOf(TransactionState.COMMITTED, TransactionState.FAILED)) {
            "transaction $id cannot be rolled back from ${journal.state}"
        }
        require(hasRollbackData(journal)) { "transaction $id has no complete rollback data" }
        return path
    }

    /** Lists validated saved rollback sets without exposing incomplete or corrupt transactions. */
    fun rollbackCandidates(): List<Path> = journals().filter { path ->
        runCatching {
            val journal = loadChecked(path)
            journal.purpose in setOf(null, "STAGE") && journal.state in setOf(TransactionState.COMMITTED, TransactionState.FAILED) && hasRollbackData(journal)
        }.getOrDefault(false)
    }.sortedByDescending { path ->
        loadChecked(path).createdAt?.let(Instant::parse) ?: Instant.MIN
    }

    /** Recovers every journal found below the transaction root. */
    fun recoverAll(offline: Boolean = false): List<TransactionResult> {
        if (!Files.isDirectory(root)) return emptyList()
        val paths = journals()
        if (!offline) require(paths.none { path ->
            val journal = loadChecked(path)
            (journal.purpose in setOf("INSTALL", "RESTORE") && journal.state == TransactionState.AWAITING_HEALTH) ||
                (journal.state in setOf(TransactionState.PUBLISHING, TransactionState.ACTIVATING, TransactionState.ROLLING_BACK) &&
                (journal.purpose in setOf("INSTALL", "RESTORE") || journal.artifacts.any {
                    Paths.get(it.target).toAbsolutePath().normalize().parent != publicationRoot.resolve("update")
                }))
        }) { "interrupted offline installation must be recovered before starting the server" }
        return Files.list(root).use { directories ->
            directories
                .filter(Files::isDirectory)
                .map { it.resolve("journal.json") }
                .filter(Files::isRegularFile)
                .filter { offline || loadChecked(it).purpose in setOf(null, "STAGE") }
                .sorted()
                .map(::recover)
                .toList()
        }
    }

    private fun rollbackInternal(path: Path, journal: TransactionJournal) {
        // Validate the whole set before changing any target.
        validateJournal(path, journal)
        require(hasRollbackData(journal)) { "transaction ${journal.id} has no complete rollback data" }
        transition(path, journal, TransactionState.ROLLING_BACK)
        journal.artifacts.forEach { record ->
            val target = Paths.get(record.target)
            if (record.targetExisted) {
                Files.createDirectories(target.parent)
                Files.copy(Paths.get(record.backup), target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.deleteIfExists(target)
            }
        }
        transition(path, journal, TransactionState.ROLLED_BACK)
    }

    private fun hasRollbackData(journal: TransactionJournal): Boolean = journal.artifacts.all { record ->
        !record.targetExisted || (Files.isRegularFile(Paths.get(record.backup)) &&
            ((record.backupSha256 ?: record.prior?.sha256)?.let { digest(Paths.get(record.backup)) == it } ?: true))
    }

    private fun loadChecked(path: Path): TransactionJournal {
        val normalized = checkedPath(path, root)
        require(normalized.fileName.toString() == "journal.json" && normalized.parent.parent == root.toAbsolutePath().normalize()) {
            "invalid transaction journal location"
        }
        require(Files.size(normalized) <= 1024 * 1024) { "transaction journal exceeds size limit" }
        return TransactionJournal.load(normalized).also { validateJournal(normalized, it) }
    }

    private fun validateJournal(path: Path, journal: TransactionJournal) {
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
            require(record.backupSha256 == null || record.backupSha256!!.matches(Regex("[0-9a-f]{64}"))) { "invalid backup digest" }
            listOfNotNull(record.expected, record.prior).forEach { version ->
                SemanticVersion.parse(version.version)
                ApiVersionRange(version.minimumApi, version.maximumApi)
                require(version.sha256 == null || version.sha256!!.matches(Regex("[0-9a-f]{64}"))) { "invalid persisted artifact digest" }
            }
            checkedPath(Paths.get(record.target), publicationRoot)
        }
        require(targets.distinct().size == targets.size) { "transaction contains duplicate targets" }
    }

    private fun checkedPath(path: Path, allowedRoot: Path): Path = UpdatePaths.inside(path, allowedRoot)

    private fun journals(): List<Path> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { directories ->
            directories.filter(Files::isDirectory)
                .map { it.resolve("journal.json") }
                .filter(Files::isRegularFile)
                .toList()
        }
    }

    internal fun readJournal(path: Path): TransactionJournal = loadChecked(path)

    internal fun pendingInstallations(): List<Path> = journals().filter { path ->
        val journal = loadChecked(path)
        journal.purpose in setOf(null, "STAGE") && (
            (journal.state == TransactionState.AWAITING_HEALTH && journal.appliedOperation != "INSTALL") ||
                (journal.state == TransactionState.ROLLED_BACK && journal.rollbackRequested && journal.appliedOperation != "RESTORE")
            )
    }

    internal fun appliedChildJournal(parent: UUID, purpose: String): Path? = journals().firstOrNull { path ->
        val journal = loadChecked(path)
        journal.parentTransaction == parent.toString() && journal.purpose == purpose && journal.state == TransactionState.COMMITTED
    }

    internal fun revertInstallation(path: Path) {
        val journal = loadChecked(path)
        require(journal.purpose in setOf("INSTALL", "RESTORE")) { "not an internal installation transaction" }
        rollbackInternal(path, journal)
    }

    internal fun markInstalled(path: Path, operation: String) {
        require(operation in setOf("INSTALL", "RESTORE"))
        val journal = loadChecked(path)
        journal.appliedOperation = operation
        TransactionJournal.save(path, journal)
    }

    private fun digest(path: Path): String = Digests.sha256(path)

    private fun transition(path: Path, journal: TransactionJournal, state: TransactionState) {
        journal.state = state
        TransactionJournal.save(path, journal)
    }
}
