package org.pnfolder.pnlibrary.core.updates.install

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupComponent
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup
import org.pnfolder.pnlibrary.api.updates.release.ProductChange
import org.pnfolder.pnlibrary.api.updates.release.UpdatePlan
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.updates.databases.DatabaseBackups
import org.pnfolder.pnlibrary.core.updates.engine.ArtifactVerifier
import org.pnfolder.pnlibrary.core.updates.engine.PendingTransaction
import org.pnfolder.pnlibrary.core.updates.engine.TransactionJournal
import org.pnfolder.pnlibrary.core.updates.engine.TrustedHttpClient
import org.pnfolder.pnlibrary.core.updates.engine.UpdateProcessSession
import org.pnfolder.pnlibrary.core.updates.engine.UpdateTransaction
import org.pnfolder.pnlibrary.core.updates.history.BackupItemText
import org.pnfolder.pnlibrary.core.updates.history.BackupStorage
import org.pnfolder.pnlibrary.core.updates.history.ConfigurationSnapshots
import org.pnfolder.pnlibrary.core.updates.history.PrivateFiles
import org.pnfolder.pnlibrary.core.updates.policy.UpdateConfiguration
import org.pnfolder.pnlibrary.core.updates.service.RegisteredUpdate
import org.pnfolder.pnlibrary.core.updates.service.UpdateHealthReport
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/**
 * Stages update files together with their safety copies, and verifies a staged transaction after the next server
 * start. Also lists, prunes and applies the saved rollback sets.
 */
internal class UpdateInstaller(
    platform: PlatformIdentity,
    dataFolder: Path,
    private val configuration: UpdateConfiguration,
    http: TrustedHttpClient,
    private val databases: DatabaseBackups,
    private val warn: (String) -> Unit = {},
) {
    private val configurationsFolder = dataFolder.resolve("updates/configurations")
    private val configurations = ConfigurationSnapshots(configurationsFolder, dataFolder.parent)
    private val libraryConfiguration = UpdateConfigurationBackup(dataFolder, listOf("config.yml"))
    private val transactionsFolder = dataFolder.resolve("updates/transactions")
    private val exportsFolder = dataFolder.resolve("updates/exports")
    private val databasesFolder = dataFolder.resolve("updates/databases")
    private val handoutFolder = dataFolder.resolve("updates/handout")
    private val transaction = UpdateTransaction(
        transactionsFolder,
        ArtifactVerifier(configuration.downloads.maxArtifactBytes),
        dataFolder.parent,
        warn,
    )
    private val stager = UpdatePlanStager(
        platform = platform.type,
        dataFolder = dataFolder,
        http = http,
        transaction = transaction,
        maximumArtifactBytes = configuration.downloads.maxArtifactBytes,
        requireSha256 = configuration.installation.requireSha256,
    )

    /** Finishes or rolls back transactions a crash interrupted, then drops sets past their retention. */
    fun recoverInterruptedTransactions() {
        transaction.recoverAll()
        pruneBackups()
    }

    /** Removes saved sets older than the retention period, except those a restart still has to verify. Returns how many went. */
    fun pruneBackups(now: Instant = Instant.now()): Int {
        val busy = busyBackups()
        val old = transaction.rollbackCandidates().filter { path ->
            val journal = TransactionJournal.load(path)
            journal.id !in busy && BackupStorage.expired(journal.createdAt?.let(Instant::parse), now)
        }
        old.forEach(::removeBackup)
        warnIfLarge()
        return old.size
    }

    /** Deletes one saved set with its configuration and data copies; a set that a restart still verifies is refused. */
    fun deleteBackup(id: UUID) {
        val path = transaction.rollbackCandidate(id)
        require(id.toString() !in busyBackups()) { "Набор $id ещё проверяется после перезапуска" }
        removeBackup(path)
    }

    /** Lets each plugin write its own copies; a plugin that fails stops the update, since the copy would be missing. */
    private fun runExports(planId: UUID, changed: List<RegisteredUpdate>) {
        changed.forEach { registration ->
            if (registration.request.exports.isEmpty()) return@forEach
            val folder = exportsFolder.resolve(planId.toString()).resolve(registration.product)
            PrivateFiles.directories(folder)
            registration.request.exports.forEach { export ->
                try {
                    export.export(folder.resolve(export.fileName))
                } catch (error: Exception) {
                    BackupStorage.delete(exportsFolder.resolve(planId.toString()))
                    throw IllegalStateException("Плагин ${registration.product} не смог сохранить копию «${export.name}»: ${error.message}", error)
                }
            }
        }
    }

    /** Puts a saved database of [product] back for the set [id]; what it replaces is kept first. */
    fun restoreDatabase(id: UUID, product: String, name: String) {
        val journal = TransactionJournal.load(transaction.rollbackCandidate(id))
        databases.restore(UUID.fromString(requireNotNull(journal.planId) { "У набора нет плана" }), product, name)
    }

    /** Copies the saved databases and exports of [product] from the set [id] into a new folder and returns it. */
    fun extractBackup(id: UUID, product: String): Path {
        val journal = TransactionJournal.load(transaction.rollbackCandidate(id))
        val plan = requireNotNull(journal.planId) { "У набора нет плана" }.toString()
        val target = handoutFolder.resolve("${id.toString().take(HANDOUT_ID)}-$product")
        val sources = listOf(databasesFolder, exportsFolder).mapNotNull { BackupStorage.child(it.resolve(plan), product) }
        require(sources.isNotEmpty()) { "В наборе $id нет сохранённых баз и выгрузок плагина $product" }
        sources.forEach { BackupStorage.copy(it, target) }
        return target
    }

    private fun busyBackups(): Set<String> =
        (transaction.awaitingHealth() + transaction.awaitingRollbackHealth()).map { transaction.readJournal(it.journal).id }.toSet() +
            transaction.pendingInstallations().map { transaction.readJournal(it).id }

    private fun removeBackup(journalPath: Path) {
        val journal = TransactionJournal.load(journalPath)
        journal.configurationSnapshot?.let { BackupStorage.delete(configurationsFolder.resolve(it)) }
        journal.planId?.let { BackupStorage.delete(exportsFolder.resolve(it)) }
        journal.planId?.let { BackupStorage.delete(databasesFolder.resolve(it)) }
        BackupStorage.delete(journalPath.parent)
    }

    private fun warnIfLarge() {
        val total = listOf(configurationsFolder, transactionsFolder, exportsFolder, databasesFolder).sumOf(BackupStorage::bytes)
        if (total < BackupStorage.LARGE_BYTES) return
        warn(
            "Резервные копии занимают ${BackupItemText.size(total)}. Старше ${BackupStorage.RETENTION.toDays()} дней удаляются сами; " +
                "копии плагинов лежат в updates/exports; ненужные можно удалить командой /pn updates backups delete <id>.",
        )
    }

    /** Saves exports, databases and configurations of the changed plugins, then downloads and prepares the plan for restart. */
    fun stage(snapshot: UpdatePlanSnapshot, registrations: List<RegisteredUpdate>) {
        require(transaction.awaitingHealth().isEmpty() &&
            transaction.awaitingRollbackHealth().isEmpty()) { "A prepared change is still awaiting restart verification" }
        require(transaction.pendingInstallations().isEmpty()) { "A prepared rollback must be applied before preparing another update" }
        val plan = requireNotNull(snapshot.plan) { "Update plan has no installable target" }

        val targets = registrations.associate { registration ->
            registration.descriptor.id to InstalledUpdateTarget(
                currentJar = registration.jar,
                updateDirectory = registration.updateDirectory,
                supportedApi = registration.descriptor.supportedApi,
            )
        }
        val changed = plan.changes.map { it.product }.toSet()
        val selections = registrations.filter { it.descriptor.id in changed }.mapNotNull { registration ->
            val selection = registration.request.configurationBackup
                ?: libraryConfiguration.takeIf { registration.product == PnLibraryIdentity.PRODUCT_ID }
            selection?.let { registration.descriptor.id to it }
        }.toMap()
        runExports(snapshot.id, registrations.filter { it.descriptor.id in changed })
        databases.copyBefore(snapshot.id, registrations.filter { it.descriptor.id in changed }.map { it.product })
        val configurationSnapshot = configurations.capture(snapshot.id, selections)
        stager.stage(snapshot.id, plan, targets, configurationSnapshot)
        warnIfLarge()
    }

    /** The saved rollback sets with the configurations and databases kept for each. */
    fun backups(): List<UpdateBackup> = transaction.rollbackCandidates().map { path ->
        val journal = TransactionJournal.load(path)
        val files = journal.configurationSnapshot
            ?.let { id -> runCatchingNonFatal { configurations.read(UUID.fromString(id)).files.filter { it.existed } }.getOrNull() }
            .orEmpty()
        UpdateBackup(UUID.fromString(journal.id), journal.createdAt?.let(Instant::parse), journal.planId?.let(UUID::fromString),
            journal.artifacts.map { UpdateBackupComponent(ProductId.of(it.component), it.expectedVersion, it.previousVersion) },
            files.size,
            files.map { UpdateBackupItem(ProductId.of(it.product), it.backupKind, it.name.orEmpty(), it.size) } +
                journal.planId?.let { databases.items(UUID.fromString(it)) }.orEmpty())
    }

    /** Prepares a rollback to the newest saved set. */
    fun rollbackLatest(): UpdatePlanSnapshot {
        val candidate = transaction.latestRollbackCandidate()
            ?: error("Нет сохранённого набора JAR для отката")
        return rollback(UUID.fromString(TransactionJournal.load(candidate).id))
    }

    /** Prepares a rollback to set [id] for the next restart, putting configurations back too when asked. */
    fun rollback(id: UUID, restoreConfigurations: Boolean = false): UpdatePlanSnapshot {
        require(transaction.awaitingHealth().isEmpty() &&
            transaction.awaitingRollbackHealth().isEmpty()) { "A prepared change is still awaiting restart verification" }
        require(transaction.pendingInstallations().isEmpty()) { "Another rollback is already prepared for restart" }
        val candidate = transaction.rollbackCandidate(id)
        val journal = TransactionJournal.load(candidate)
        if (restoreConfigurations) {
            val saved = requireNotNull(journal.configurationSnapshot) { "У этого набора нет снимка конфигураций" }
            require(configurations.read(UUID.fromString(saved)).planId == journal.planId) { "Configuration snapshot belongs to another plan" }
        }
        // Legacy backups may lack version information. Never invent a version from the current plan.
        val changes = journal.artifacts.mapNotNull { artifact ->
            val updated = artifact.expectedVersion?.let(SemanticVersion::tryParse)
            val previous = artifact.previousVersion?.let(SemanticVersion::tryParse)
            if (previous == null || updated == null) null else ProductChange(ProductId.of(artifact.component), previous, updated)
        }
        val snapshot = UpdatePlanSnapshot(journal.planId?.let(UUID::fromString) ?: id, 0, UpdateState.ROLLED_BACK,
            journal.targetApi?.let { UpdatePlan(it, changes, emptyList()) }, emptyList(),
            "Резервный набор $id подготовлен к следующему перезапуску" +
            "${if (restoreConfigurations) " с восстановлением конфигураций" else " без изменения конфигураций"}.")
        transaction.rollback(candidate, restoreConfigurations)
        return snapshot
    }

    /** After a restart, compares the versions that loaded with those a prepared set expected and reports the outcome. */
    fun reconcilePending(
        registrations: List<RegisteredUpdate>,
        reportHealth: (UpdateHealthReport) -> Unit,
    ) = reconcileVersions(registrations.associate { it.product to it.version }, reportHealth)

    /** Reports every set that waits for verification, oldest first, and closes it as healthy or failed. */
    internal fun reconcileVersions(
        installedVersions: Map<String, String>,
        reportHealth: (UpdateHealthReport) -> Unit,
        recoveredFailuresOnly: Boolean = false,
    ) {
        pendingChecks(installedVersions, recoveredFailuresOnly).forEach { (pending, source) ->
            val mismatches = pending.expectedVersions.filter { (product, expected) -> installedVersions[product] != expected }
            val healthy = mismatches.isEmpty() && pending.unknownProducts.isEmpty()
            val id = UUID.fromString(source.id)
            val message = "Набор $id. " + outcome(pending, mismatches, installedVersions, healthy)
            reportHealth(UpdateHealthReport(id, source.planId?.let(UUID::fromString) ?: id, healthChanges(pending, source), healthy, pending.rollback, message))
            if (pending.rollback) transaction.completeRollbackHealth(pending.journal, healthy)
            else transaction.completeHealth(pending.journal, healthy)
        }
    }

    private fun pendingChecks(installedVersions: Map<String, String>, recoveredFailuresOnly: Boolean) = run {
        val recovered = transaction.failedHealthChecks().filter { pending ->
            pending.unknownProducts.isEmpty() && pending.expectedVersions.all { (product, expected) -> installedVersions[product] == expected }
        }
        val awaiting = if (recoveredFailuresOnly) emptyList() else transaction.awaitingHealth() + transaction.awaitingRollbackHealth()
        (awaiting + recovered)
            .filter { it.preparedSession != UpdateProcessSession.id }
            .map { it to transaction.readJournal(it.journal) }
            .sortedBy { it.second.createdAt?.let(Instant::parse) ?: Instant.MIN }
    }

    private fun outcome(
        pending: PendingTransaction,
        mismatches: Map<String, String>,
        installedVersions: Map<String, String>,
        healthy: Boolean,
    ): String {
        if (healthy) return "Подтверждены загруженные версии: ${pending.expectedVersions.entries.joinToString { "${it.key}=${it.value}" }}"
        val details = mismatches.entries.map { (product, expected) ->
            "$product: ожидалась $expected, загружена ${installedVersions[product] ?: "не загружена"}"
        } + pending.unknownProducts.map { "$it: ожидаемая версия не записана в старом журнале" }
        return "Версии после перезапуска не подтвердились: ${details.joinToString()}"
    }

    private fun healthChanges(pending: PendingTransaction, source: TransactionJournal): List<ProductChange> =
        source.artifacts.mapNotNull { artifact ->
            val previous = artifact.previousVersion?.let(SemanticVersion::tryParse)
            val updated = artifact.expectedVersion?.let(SemanticVersion::tryParse)
            val product = ProductId.of(artifact.component)
            when {
                pending.rollback && previous != null && updated != null -> ProductChange(product, updated, previous)
                !pending.rollback && updated != null && (previous != null || !artifact.targetExisted) ->
                    ProductChange(product, previous, updated)
                else -> null
            }
        }

    /** Closes sets whose check failed earlier but whose expected versions are loaded now. */
    fun reconcileRecoveredFailures(registrations: List<RegisteredUpdate>, reportHealth: (UpdateHealthReport) -> Unit) =
        reconcileVersions(registrations.associate { it.product to it.version }, reportHealth, recoveredFailuresOnly = true)

    /** True when nothing waits for verification and none of [products] has a failed check, so automatic updates may go on. */
    fun automaticUpdatesHealthy(products: Set<ProductId>): Boolean = transaction.awaitingHealth().isEmpty() &&
        transaction.awaitingRollbackHealth().isEmpty() && transaction.automaticUpdatesHealthy(products.map { it.value }.toSet())

    /** True when a prepared set expects a product that has not registered yet, so verification must wait. */
    fun awaitingRegistrations(registrations: List<RegisteredUpdate>): Boolean {
        val installed = registrations.map { it.product }.toSet()
        return (transaction.awaitingHealth() + transaction.awaitingRollbackHealth())
            .filter { it.preparedSession != UpdateProcessSession.id }
            .any { pending -> (pending.expectedVersions.keys + pending.unknownProducts).any { it !in installed } }
    }

    private companion object {
        /** Characters of the set id used in a handout folder name. */
        const val HANDOUT_ID = 8
    }
}
