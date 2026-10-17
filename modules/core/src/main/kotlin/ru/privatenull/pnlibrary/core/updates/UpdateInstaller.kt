package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.updates.UpdatePlan
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateBackup
import ru.privatenull.pnlibrary.api.updates.UpdateBackupComponent
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.ProductChange
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.updates.UpdateConfigurationBackup
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.updates.engine.TransactionJournal
import ru.privatenull.pnlibrary.core.updates.engine.UpdateProcessSession
import java.time.Instant
import java.util.UUID
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.core.updates.engine.ArtifactVerifier
import ru.privatenull.pnlibrary.core.updates.engine.TrustedHttpClient
import ru.privatenull.pnlibrary.core.updates.engine.UpdateTransaction
import java.nio.file.Path

/** Stages update files and verifies a staged transaction after the next server start. */
internal class UpdateInstaller(
    platform: PlatformIdentity,
    dataFolder: Path,
    private val configuration: UpdateConfiguration,
    http: TrustedHttpClient,
) {
    private val configurations = ConfigurationSnapshots(dataFolder.resolve("updates/configurations"), dataFolder.parent)
    private val libraryConfiguration = UpdateConfigurationBackup(dataFolder, listOf("updates.yml"))
    private val transaction = UpdateTransaction(
        dataFolder.resolve("updates/transactions"),
        ArtifactVerifier(MAXIMUM_ARTIFACT_BYTES),
        dataFolder.parent,
    )
    private val stager = UpdatePlanStager(
        platform = platform.type,
        dataFolder = dataFolder,
        http = http,
        transaction = transaction,
        maximumArtifactBytes = MAXIMUM_ARTIFACT_BYTES,
        requireSha256 = configuration.installation.requireSha256,
    )

    fun recoverInterruptedTransactions() {
        transaction.recoverAll()
    }

    fun stage(snapshot: UpdatePlanSnapshot, registrations: List<RegisteredUpdate>) {
        require(transaction.awaitingHealth().isEmpty() && transaction.awaitingRollbackHealth().isEmpty()) { "A prepared change is still awaiting restart verification" }
        require(transaction.pendingInstallations().isEmpty()) { "A prepared rollback must be applied before preparing another update" }
        val plan = requireNotNull(snapshot.plan) { "update plan has no installable target" }
        verifyPluginDownloadsAllowed(plan)

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
                ?: libraryConfiguration.takeIf { registration.product == "pnlibrary" }
            selection?.let { registration.descriptor.id to it }
        }.toMap()
        val configurationSnapshot = configurations.capture(snapshot.id, selections)
        stager.stage(snapshot.id, plan, targets, configurationSnapshot)
    }

    fun backups(): List<UpdateBackup> = transaction.rollbackCandidates().map { path ->
        val journal = TransactionJournal.load(path)
        UpdateBackup(UUID.fromString(journal.id), journal.createdAt?.let(Instant::parse), journal.planId?.let(UUID::fromString),
            journal.artifacts.map { UpdateBackupComponent(ProductId.of(it.component), it.expectedVersion, it.previousVersion) },
            journal.configurationSnapshot?.let { id -> runCatching { configurations.read(UUID.fromString(id)).files.size }.getOrDefault(0) } ?: 0)
    }

    fun rollbackLatest(): UpdatePlanSnapshot {
        val candidate = transaction.latestRollbackCandidate()
            ?: error("Нет сохранённого набора JAR для отката")
        return rollback(UUID.fromString(TransactionJournal.load(candidate).id))
    }

    fun rollback(id: UUID, restoreConfigurations: Boolean = false): UpdatePlanSnapshot {
        require(transaction.awaitingHealth().isEmpty() && transaction.awaitingRollbackHealth().isEmpty()) { "A prepared change is still awaiting restart verification" }
        require(transaction.pendingInstallations().isEmpty()) { "Another rollback is already prepared for restart" }
        val candidate = transaction.rollbackCandidate(id)
        val journal = TransactionJournal.load(candidate)
        if (restoreConfigurations) {
            val saved = requireNotNull(journal.configurationSnapshot) { "У этого набора нет снимка конфигураций" }
            require(configurations.read(UUID.fromString(saved)).planId == journal.planId) { "configuration snapshot belongs to another plan" }
        }
        // Legacy backups may lack version information. Never invent a version from the current plan.
        val changes = journal.artifacts.mapNotNull { artifact ->
            val updated = artifact.expectedVersion?.let(SemanticVersion::tryParse)
            val previous = artifact.previousVersion?.let(SemanticVersion::tryParse)
            if (previous == null || updated == null) null else ProductChange(ProductId.of(artifact.component), previous, updated)
        }
        val snapshot = UpdatePlanSnapshot(journal.planId?.let(UUID::fromString) ?: id, 0, UpdateState.ROLLED_BACK,
            journal.targetApi?.let { UpdatePlan(it, changes, emptyList()) }, emptyList(),
            "Резервный набор $id подготовлен к следующему перезапуску${if (restoreConfigurations) " с восстановлением конфигураций" else " без изменения конфигураций"}.")
        transaction.rollback(candidate, restoreConfigurations)
        return snapshot
    }

    fun reconcilePending(
        registrations: List<RegisteredUpdate>,
        reportHealth: (UpdateHealthReport) -> Unit,
    ) = reconcileVersions(registrations.associate { it.product to it.version }, reportHealth)

    internal fun reconcileVersions(installedVersions: Map<String, String>, reportHealth: (UpdateHealthReport) -> Unit, recoveredFailuresOnly: Boolean = false) {
        val recovered = transaction.failedHealthChecks().filter { pending ->
            pending.unknownProducts.isEmpty() && pending.expectedVersions.all { (product, expected) -> installedVersions[product] == expected }
        }
        val pendingChecks = ((if (recoveredFailuresOnly) emptyList() else transaction.awaitingHealth() + transaction.awaitingRollbackHealth()) + recovered)
            .filter { it.preparedSession != UpdateProcessSession.id }
            .map { it to transaction.readJournal(it.journal) }
            .sortedBy { it.second.createdAt?.let(Instant::parse) ?: Instant.MIN }
        pendingChecks.forEach { (pending, source) ->
            val mismatches = pending.expectedVersions.filter { (product, expected) ->
                installedVersions[product] != expected
            }
            val healthy = mismatches.isEmpty() && pending.unknownProducts.isEmpty()
            val details = mismatches.entries.map { (product, expected) ->
                "$product: ожидалась $expected, загружена ${installedVersions[product] ?: "не загружена"}"
            } + pending.unknownProducts.map { "$it: ожидаемая версия не записана в старом журнале" }
            val changes = source.artifacts.mapNotNull { artifact ->
                val previous = artifact.previousVersion?.let(SemanticVersion::tryParse)
                val updated = artifact.expectedVersion?.let(SemanticVersion::tryParse)
                when {
                    pending.rollback && previous != null && updated != null -> ProductChange(ProductId.of(artifact.component), updated, previous)
                    !pending.rollback && updated != null && (previous != null || !artifact.targetExisted) -> ProductChange(ProductId.of(artifact.component), previous, updated)
                    else -> null
                }
            }
            val outcome = if (healthy) "Подтверждены загруженные версии: ${pending.expectedVersions.entries.joinToString { "${it.key}=${it.value}" }}"
                else "Версии после перезапуска не подтвердились: ${details.joinToString()}"
            val id = UUID.fromString(source.id)
            reportHealth(UpdateHealthReport(id, source.planId?.let(UUID::fromString) ?: id, changes, healthy, pending.rollback, "Набор $id. $outcome"))
            if (pending.rollback) transaction.completeRollbackHealth(pending.journal, healthy)
            else transaction.completeHealth(pending.journal, healthy)
        }
    }

    fun reconcileRecoveredFailures(registrations: List<RegisteredUpdate>, reportHealth: (UpdateHealthReport) -> Unit) =
        reconcileVersions(registrations.associate { it.product to it.version }, reportHealth, recoveredFailuresOnly = true)

    fun automaticUpdatesHealthy(products: Set<ProductId>): Boolean = transaction.awaitingHealth().isEmpty() &&
        transaction.awaitingRollbackHealth().isEmpty() && transaction.automaticUpdatesHealthy(products.map { it.value }.toSet())

    fun awaitingRegistrations(registrations: List<RegisteredUpdate>): Boolean {
        val installed = registrations.map { it.product }.toSet()
        return (transaction.awaitingHealth() + transaction.awaitingRollbackHealth())
            .filter { it.preparedSession != UpdateProcessSession.id }
            .any { pending -> (pending.expectedVersions.keys + pending.unknownProducts).any { it !in installed } }
    }

    private fun verifyPluginDownloadsAllowed(plan: UpdatePlan) {
        plan.changes
            .filterNot { change -> change.product.value == "pnlibrary" }
            .forEach { change ->
                val policy = configuration.plugins[change.product.value]
                require(configuration.pluginUpdatesEnabled && policy?.enabled != false) {
                    "Загрузка плагина ${change.product} отключена политикой обновлений"
                }
            }
    }

    private companion object {
        const val MAXIMUM_ARTIFACT_BYTES = 512L * 1024L * 1024L
    }
}
