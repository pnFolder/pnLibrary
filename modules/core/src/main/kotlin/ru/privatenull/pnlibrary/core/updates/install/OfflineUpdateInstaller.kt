package ru.privatenull.pnlibrary.core.updates.install

import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.updates.engine.ArtifactSpecification
import ru.privatenull.pnlibrary.core.updates.engine.ArtifactVerifier
import ru.privatenull.pnlibrary.core.updates.engine.TransactionArtifact
import ru.privatenull.pnlibrary.core.updates.engine.TransactionState
import ru.privatenull.pnlibrary.core.updates.engine.UpdatePaths
import ru.privatenull.pnlibrary.core.updates.engine.UpdateTransaction
import ru.privatenull.pnlibrary.core.updates.history.ConfigurationRestorer
import ru.privatenull.pnlibrary.core.updates.history.ConfigurationSnapshots
import ru.privatenull.pnlibrary.core.updates.service.UpdateInstallationLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

/** Pre-start installer. Run from its separate updater JAR, never from a loaded server plugin JAR. */
object OfflineUpdateInstaller {
    /** Requires explicit stopped-server acknowledgement; an active runtime also holds a file lock. */
    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size == 2 && arguments[0] == "--server-stopped") {
            "Usage: java -jar pnLibrary-updater.jar --server-stopped <plugins/pnLibrary>"
        }
        val applied = apply(Paths.get(arguments[1]))
        println("Applied $applied prepared component sets. Start the server to verify loaded versions.")
    }

    internal fun apply(dataDirectory: Path): Int {
        val data = dataDirectory.toAbsolutePath().normalize()
        require(Files.isDirectory(data) && data.parent != null) { "library data directory does not exist" }
        return UpdateInstallationLock.acquire(data).use {
            val plugins = data.parent
            val verifier = ArtifactVerifier(512L * 1024 * 1024)
            val transactions = UpdateTransaction(data.resolve("updates/transactions"), verifier, plugins)
            val snapshots = ConfigurationSnapshots(data.resolve("updates/configurations"), plugins)
            val restorer = ConfigurationRestorer(snapshots, data.resolve("updates/configuration-restores"))
            restorer.recoverInterrupted()
            transactions.recoverAll(offline = true)
            val pending = transactions.pendingInstallations().map { path -> prepare(transactions, verifier, snapshots, plugins, path) }
            require(pending.flatMap { it.artifacts }.map { it.target }.distinct().size == pending.sumOf { it.artifacts.size }) {
                "conflicting prepared component sets; resolve them before starting the server"
            }
            pending.forEach { prepared ->
                val child = transactions.appliedChildJournal(prepared.id, prepared.purpose) ?: run {
                    val result = transactions.prepareForRestart(prepared.artifacts, purpose = prepared.purpose, parentTransaction = prepared.id)
                    // This child commits disk publication, not server/plugin health.
                    transactions.completeHealth(result.journal, true)
                    result.journal
                }
                var restoredConfigurations: UUID? = null
                try {
                    restoredConfigurations = prepared.configurationSnapshot?.let { restorer.restore(it, prepared.id) }
                    prepared.pendingFiles.zip(prepared.artifacts).forEach { (file, artifact) ->
                        if (Files.exists(file)) {
                            verifier.verify(file, artifact.specification.copy(fileName = file.fileName.toString()))
                            Files.delete(file)
                        }
                    }
                    transactions.markInstalled(prepared.journal, prepared.purpose)
                } catch (error: Throwable) {
                    restoredConfigurations?.let { runCatching { restorer.undo(it) }.onFailure(error::addSuppressed) }
                    runCatching { transactions.revertInstallation(child) }.onFailure(error::addSuppressed)
                    throw error
                }
            }
            pending.size
        }
    }

    private data class Prepared(val id: UUID, val journal: Path, val purpose: String,
        val artifacts: List<TransactionArtifact>, val pendingFiles: List<Path>, val configurationSnapshot: UUID?)

    private fun prepare(transactions: UpdateTransaction, verifier: ArtifactVerifier, snapshots: ConfigurationSnapshots, plugins: Path, path: Path): Prepared {
        val journal = transactions.readJournal(path)
        val restoring = journal.state == TransactionState.ROLLED_BACK
        val configuration = if (restoring && journal.restoreConfigurations) {
            val id = UUID.fromString(requireNotNull(journal.configurationSnapshot) { "no configuration snapshot was retained" })
            require(snapshots.read(id).planId == journal.planId) { "configuration snapshot belongs to another plan" }
            id
        } else null
        val artifacts = journal.artifacts.map { record ->
            val installed = requireNotNull(record.installedTarget) { "legacy transaction lacks an installation target; manual installation is required" }
            val target = UpdatePaths.inside(Paths.get(installed), plugins)
            require(target.parent == plugins && target.fileName.toString().endsWith(".jar", true)) { "invalid installed JAR target" }
            val pending = Paths.get(record.target).toAbsolutePath().normalize()
            require(pending.parent == plugins.resolve("update") && pending.fileName == target.fileName) { "invalid prepared update location" }
            if (restoring) require(record.targetExisted) { "rollback of newly installed components requires explicit removal support" }
            val version = requireNotNull(if (restoring) record.prior else record.expected) { "transaction lacks verified version metadata" }
            val hash = requireNotNull(version.sha256) { "transaction lacks a preserved artifact checksum" }
            val source = Paths.get(if (restoring) record.backup else record.staged)
            val specification = ArtifactSpecification(ProductId.of(record.component), SemanticVersion.parse(version.version),
                ApiVersionRange(version.minimumApi, version.maximumApi), source.fileName.toString(), Files.size(source), hash)
            verifier.verify(source, specification)
            TransactionArtifact(specification, source, target)
        }
        return Prepared(UUID.fromString(journal.id), path, if (restoring) "RESTORE" else "INSTALL", artifacts,
            journal.artifacts.map { Paths.get(it.target) }, configuration)
    }
}
