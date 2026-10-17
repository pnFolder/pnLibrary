package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateHistoryEntry
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.updates.ProductChange
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import ru.privatenull.pnlibrary.update.RefreshMode
import ru.privatenull.pnlibrary.spi.platform.PlatformExecution
import ru.privatenull.pnlibrary.spi.platform.PlatformReadiness
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.update.ResolutionResult
import java.util.concurrent.Executors
import java.nio.file.Path

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
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        fun attempt() {
            if (closed.get()) return
            try {
                val installed = registrations()
                if (installer.awaitingRegistrations(installed) && System.nanoTime() < deadline) {
                    executor.schedule(::attempt, 250, java.util.concurrent.TimeUnit.MILLISECONDS)
                    return
                }
                installer.reconcilePending(installed, ::recordHealth)
                created.start()
            } catch (error: Exception) {
                if (!closed.get()) logging.log(this, LogLevel.WARNING, "Could not verify restarted component versions; automatic checks were not started", error)
            }
        }
        if (!closed.get()) try { executor.execute(::attempt) }
        catch (error: java.util.concurrent.RejectedExecutionException) { if (!closed.get()) throw error }
    }

    private fun recordHealth(report: UpdateHealthReport) {
        journal.recordHealth(report)
        if (!closed.get()) runCatching { logging.log(this, if (report.healthy) LogLevel.INFO else LogLevel.WARNING, report.message) }
    }

    fun historyPage(page: Int, product: String?): CompletionStage<List<UpdateHistoryEntry>> = readAsync {
        require(page > 0) { "page must be positive" }
        val normalized = product?.let { ProductId.of(it).value }
        journal.read(page, product = normalized).map { event ->
            UpdateHistoryEntry(event.id, event.occurredAt, event.planId, UpdateState.valueOf(event.state),
                event.actor, event.operation, event.changes.map { change ->
                    ProductChange(ProductId.of(change.product), change.from?.let(SemanticVersion::parse), SemanticVersion.parse(change.to))
                }, event.message)
        }.let { java.util.Collections.unmodifiableList(it) }
    }

    fun <T> readAsync(action: () -> T): CompletionStage<T> {
        val result = CompletableFuture<T>()
        pendingReads.add(result)
        result.whenComplete { _, _ -> pendingReads.remove(result) }
        try {
            check(!closed.get()) { "update service is closed" }
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
        pendingReads.forEach { it.completeExceptionally(IllegalStateException("update service is closed")) }
        try { orchestrator.close() } finally { releaseLockWhenIdle() }
    }

    private fun releaseLockWhenIdle() {
        // shutdownNow cannot interrupt every filesystem operation. Keep the offline installer out
        // until an already-running stage operation has actually stopped writing.
        if (executor.isTerminated) { installationLock.close(); return }
        Thread({
            while (!executor.isTerminated) {
                try { executor.awaitTermination(1, java.util.concurrent.TimeUnit.DAYS) }
                catch (_: InterruptedException) { /* The lock must remain held until writes stop. */ }
            }
            installationLock.close()
        }, "pnLibrary-update-lock-release").apply { isDaemon = true; start() }
    }
}
