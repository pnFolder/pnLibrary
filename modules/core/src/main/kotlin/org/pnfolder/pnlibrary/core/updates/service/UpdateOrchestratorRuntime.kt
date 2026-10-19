package org.pnfolder.pnlibrary.core.updates.service

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.updates.ProductChange
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateHistoryEntry
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.core.updates.engine.RefreshMode
import org.pnfolder.pnlibrary.core.updates.engine.ResolutionResult
import org.pnfolder.pnlibrary.core.updates.history.UpdateEventJournal
import org.pnfolder.pnlibrary.core.updates.history.UpdateStateStore
import org.pnfolder.pnlibrary.core.updates.install.ComponentLedger
import org.pnfolder.pnlibrary.core.updates.install.UpdateInstaller
import org.pnfolder.pnlibrary.core.updates.policy.UpdateConfiguration
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformReadiness
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the scheduler and lifecycle wiring around the update orchestrator. */
internal class UpdateOrchestratorRuntime(
    private val configuration: UpdateConfiguration,
    dataFolder: Path,
    private val logging: PlatformLogging,
    readiness: PlatformReadiness,
    installer: UpdateInstaller,
    registrations: () -> List<RegisteredUpdate>,
    resolveGraph: (RefreshMode) -> ResolutionResult,
    stageGraph: (UpdatePlanSnapshot) -> Unit,
    announce: (UpdatePlanSnapshot) -> Unit,
    automaticAllowed: (UpdatePlanSnapshot) -> Boolean,
    automaticOverridesActive: () -> Boolean = { false },
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val pendingReads = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()
    private val executor = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "pnLibrary-update-orchestrator").apply { isDaemon = true }
    }

    private val installationLock = UpdateInstallationLock.acquire(dataFolder)
    private val journal by lazy { UpdateEventJournal(dataFolder.resolve("updates/events"), warning = {
        logging.log(this, LogLevel.WARNING, it)
    }) }

    private val componentLedger by lazy {
        ComponentLedger(dataFolder.resolve("updates/components.json"), journal, warning = {
            logging.log(this, LogLevel.WARNING, it)
        })
    }
    private val verifiedByHealth = ConcurrentHashMap.newKeySet<String>()

    val orchestrator = try { UpdateOrchestrator(
        configuration,
        UpdateStateStore(dataFolder.resolve("updates"), warning = { logging.log(this, LogLevel.WARNING, it) }),
        executor,
        { resolveGraph(RefreshMode.CACHED) },
        stageGraph,
        announce,
        automaticAllowed = automaticAllowed,
        remoteResolver = { resolveGraph(RefreshMode.FORCE_REMOTE) },
        beforeCheck = { installer.reconcileRecoveredFailures(registrations(), ::recordHealth) },
        automaticOverridesActive = automaticOverridesActive,
        observeState = { snapshot, actor, operation ->
            runCatching { journal.record(snapshot, actor, operation) }.onFailure { error ->
                // An observation failure must not turn an already staged transaction into a failed operation.
                if (!closed.get()) runCatching { logging.log(this, LogLevel.WARNING, "Could not persist update observation", error) }
            }
        },
    ).also { created ->
        installer.recoverInterruptedTransactions()
        readiness.whenServerReady(Runnable {
            beginStartupHealth(created, installer, registrations)
        })
    } } catch (error: Throwable) {
        executor.shutdownNow()
        runCatching { releaseLockWhenIdle() }.onFailure(error::addSuppressed)
        throw error
    }

    private fun beginStartupHealth(created: UpdateOrchestrator, installer: UpdateInstaller, registrations: () -> List<RegisteredUpdate>) {
        // Proxy readiness can precede other plugins' initialization listeners. Wait for expected
        // managed registrations, with a deadline, rather than falsely marking them absent.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        fun attempt() {
            if (closed.get()) return
            try {
                val installed = registrations()
                if (installer.awaitingRegistrations(installed) && System.nanoTime() < deadline) {
                    executor.schedule(::attempt, 250, TimeUnit.MILLISECONDS)
                    return
                }
                installer.reconcilePending(installed, ::recordHealth)
                observeComponents(installed)
                created.start()
            } catch (error: Exception) {
                if (!closed.get()) logging.log(this, LogLevel.WARNING, "Could not verify restarted component versions; automatic checks were not started", error)
            }
        }
        if (!closed.get()) try { executor.execute(::attempt) }
        catch (error: RejectedExecutionException) { if (!closed.get()) throw error }
    }

    /** Records components that appeared, disappeared or changed since the previous start. */
    /** Records a change an administrator made to one component's settings and remembers it for the next start. */
    fun recordSettingsChange(registration: RegisteredUpdate, message: String, actor: UpdateActor) {
        runCatching {
            journal.recordComponent(
                ComponentLedger.OPERATION_SETTINGS_CHANGED, registration.product, registration.version, registration.version,
                message, actor,
            )
            val snapshot = registration.snapshot
            componentLedger.updateSettings(registration.product, snapshot.channel.name.lowercase(), snapshot.automaticDownload)
        }.onFailure { error ->
            if (!closed.get()) logging.log(this, LogLevel.WARNING, "Could not record a settings change", error)
        }
    }

    private fun observeComponents(installed: List<RegisteredUpdate>) {
        runCatching {
            componentLedger.observe(
                installed.associate { registration ->
                    val snapshot = registration.snapshot
                    registration.product to ComponentLedger.Component(
                        registration.version, snapshot.channel.name.lowercase(), snapshot.automaticDownload,
                    )
                },
                verifiedByHealth.toSet(),
            )
        }.onFailure { error ->
            if (!closed.get()) logging.log(this, LogLevel.WARNING, "Could not record component changes", error)
        }
    }

    private fun recordHealth(report: UpdateHealthReport) {
        if (report.healthy) report.changes.forEach { verifiedByHealth += it.product.value }
        journal.recordHealth(report)
        if (!closed.get()) runCatching { logging.log(this, if (report.healthy) LogLevel.INFO else LogLevel.WARNING, report.message) }
    }

    fun historyPage(page: Int, product: String?): CompletionStage<List<UpdateHistoryEntry>> = readAsync {
        require(page > 0) { "Page must be positive" }
        val normalized = product?.let { ProductId.of(it).value }
        journal.read(page, product = normalized).map { event ->
            UpdateHistoryEntry(event.id, event.occurredAt, event.planId, UpdateState.valueOf(event.state),
                event.actor, event.operation, event.changes.map { change ->
                    ProductChange(ProductId.of(change.product), change.from?.let(SemanticVersion::parse), SemanticVersion.parse(change.to))
                }, event.message)
        }.let { Collections.unmodifiableList(it) }
    }

    fun historyCount(product: String?): CompletionStage<Int> = readAsync {
        journal.count(product?.let { ProductId.of(it).value })
    }

    fun <T> readAsync(action: () -> T): CompletionStage<T> {
        val result = CompletableFuture<T>()
        pendingReads.add(result)
        result.whenComplete { _, _ -> pendingReads.remove(result) }
        try {
            check(!closed.get()) { "Update service is closed" }
            executor.execute {
                if (result.isDone) return@execute
                runCatching(action).fold(result::complete, result::completeExceptionally)
            }
        } catch (error: Exception) {
            result.completeExceptionally(error)
        }
        return result
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pendingReads.forEach { it.completeExceptionally(IllegalStateException("Update service is closed")) }
        try { orchestrator.close() } finally { releaseLockWhenIdle() }
    }

    private fun releaseLockWhenIdle() {
        // shutdownNow cannot interrupt every filesystem operation. Keep the offline installer out
        // until an already-running stage operation has actually stopped writing.
        if (executor.isTerminated) { installationLock.close(); return }
        Thread({
            while (!executor.isTerminated) {
                try { executor.awaitTermination(1, TimeUnit.DAYS) }
                catch (_: InterruptedException) { /* The lock must remain held until writes stop. */ }
            }
            installationLock.close()
        }, "pnLibrary-update-lock-release").apply { isDaemon = true; start() }
    }
}
