package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.update.ResolutionResult
import java.time.Clock
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class UpdateOrchestrator(
    private val configuration: UpdateConfiguration,
    private val store: UpdateStateStore,
    private val executor: ScheduledExecutorService,
    private val resolver: () -> ResolutionResult,
    private val stageAction: (UpdatePlanSnapshot) -> Unit,
    private val announcement: (UpdatePlanSnapshot) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
    private val automaticAllowed: (UpdatePlanSnapshot) -> Boolean = { true },
    private val remoteResolver: (() -> ResolutionResult)? = null,
) : AutoCloseable {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private var inFlight: CompletableFuture<UpdatePlanSnapshot>? = null
    private var inFlightForced = false
    private var current: UpdatePlanSnapshot? = store.current()
    private var revision = current?.revision ?: 0
    private var announcedFingerprint: String? = null
    private var announcedAt: Instant? = null
    private val sessionHistory = ArrayDeque<UpdatePlanSnapshot>()

    fun start() {
        if (!configuration.effectiveChecksEnabled || closed.get()) return
        executor.scheduleWithFixedDelay(
            { checkNow(false).exceptionally { null } },
            0,
            configuration.checks.interval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    fun checkNow(forceRemote: Boolean = true): CompletionStage<UpdatePlanSnapshot> = synchronized(lock) {
        check(!closed.get()) { "update orchestrator is closed" }
        inFlight?.let { active ->
            if (!forceRemote || inFlightForced) return@synchronized active
            return@synchronized active.handle { _, _ -> Unit }.thenCompose { checkNow(true) }
        }
        val promise = CompletableFuture<UpdatePlanSnapshot>()
        inFlight = promise
        inFlightForced = forceRemote
        try {
            executor.execute { performCheck(promise, forceRemote) }
        } catch (error: Throwable) {
            releaseInFlight(promise)
            promise.completeExceptionally(error)
        }
        promise
    }

    private fun performCheck(promise: CompletableFuture<UpdatePlanSnapshot>, forceRemote: Boolean) {
        val completed = runCatching {
            val checked = snapshot(resolve(forceRemote))
            publish(checked)
            automaticallyStageIfAllowed(checked)
        }.getOrElse { error ->
            failedSnapshot(error).also(::publish)
        }
        releaseInFlight(promise)
        promise.complete(completed)
    }

    private fun resolve(forceRemote: Boolean): ResolutionResult =
        if (forceRemote) remoteResolver?.invoke() ?: resolver() else resolver()

    private fun automaticallyStageIfAllowed(snapshot: UpdatePlanSnapshot): UpdatePlanSnapshot {
        val shouldStage = configuration.effectiveAutomaticDownloads &&
            snapshot.state == UpdateState.UPDATE_AVAILABLE && automaticAllowed(snapshot)
        if (!shouldStage) return snapshot
        stageInternal(snapshot)
        return requireNotNull(current)
    }

    private fun failedSnapshot(error: Throwable): UpdatePlanSnapshot {
        val previous = synchronized(lock) { current }
        return UpdatePlanSnapshot(
            UUID.randomUUID(), ++revision, UpdateState.FAILED,
            previous?.plan, previous?.blockers.orEmpty(), rootMessage(error),
        )
    }

    private fun releaseInFlight(promise: CompletableFuture<UpdatePlanSnapshot>) = synchronized(lock) {
        if (inFlight === promise) {
            inFlight = null
            inFlightForced = false
        }
    }

    fun currentPlan(): Optional<UpdatePlanSnapshot> = Optional.ofNullable(synchronized(lock) { current })

    /** Queues a fresh graph check after any currently running check has released the coalescing slot. */
    fun registrationsChanged() {
        if (closed.get() || !configuration.effectiveChecksEnabled) return
        try {
            executor.execute { if (!closed.get()) checkNow(false) }
        } catch (_: RejectedExecutionException) {
            // A concurrent executor shutdown is a normal lifecycle race. The owner
            // will close the orchestrator and no follow-up check is required.
        }
    }

    fun stage(planId: UUID): CompletionStage<UpdatePlanSnapshot> {
        val promise = CompletableFuture<UpdatePlanSnapshot>()
        try {
            executor.execute {
                try {
                    val selected = synchronized(lock) { current }
                    require(selected != null && selected.id == planId) { "stale or unknown update plan: $planId" }
                    require(selected.state == UpdateState.UPDATE_AVAILABLE) { "update plan is not available for staging" }
                    stageInternal(selected)
                    promise.complete(requireNotNull(current))
                } catch (error: Throwable) { promise.completeExceptionally(error) }
            }
        } catch (error: Throwable) {
            promise.completeExceptionally(error)
        }
        return promise
    }

    fun history(): List<UpdatePlanSnapshot> = synchronized(lock) {
        java.util.Collections.unmodifiableList(ArrayList(sessionHistory))
    }

    fun rollback(action: () -> Unit): CompletionStage<UpdatePlanSnapshot> {
        val promise = CompletableFuture<UpdatePlanSnapshot>()
        try {
            executor.execute {
                try {
                    val selected = synchronized(lock) { current }
                        ?: error("no update plan is available for rollback")
                    action()
                    val restored = UpdatePlanSnapshot(
                        selected.id,
                        selected.revision,
                        UpdateState.ROLLED_BACK,
                        selected.plan,
                        selected.blockers,
                        "Предыдущие версии JAR подготовлены к следующему перезапуску.",
                    )
                    publish(restored)
                    promise.complete(restored)
                } catch (error: Throwable) {
                    promise.completeExceptionally(error)
                }
            }
        } catch (error: Throwable) {
            promise.completeExceptionally(error)
        }
        return promise
    }

    fun healthResolved(healthy: Boolean, message: String) {
        if (closed.get()) return
        val previous = synchronized(lock) { current }
        val resolved = UpdatePlanSnapshot(
            previous?.id ?: UUID.randomUUID(),
            ++revision,
            if (healthy) UpdateState.UP_TO_DATE else UpdateState.FAILED,
            previous?.plan,
            previous?.blockers.orEmpty(),
            message,
        )
        publish(resolved)
    }

    private fun snapshot(result: ResolutionResult): UpdatePlanSnapshot {
        val plan = when (result) { is ResolutionResult.Ready -> result.plan; is ResolutionResult.Blocked -> result.fallbackPlan }
        val blockers = if (result is ResolutionResult.Blocked) result.reasons else emptyList()
        val state = when {
            blockers.isNotEmpty() -> UpdateState.BLOCKED
            plan == null || plan.changes.isEmpty() -> UpdateState.UP_TO_DATE
            else -> UpdateState.UPDATE_AVAILABLE
        }
        val fingerprint = fingerprint(state, plan, blockers)
        val previous = synchronized(lock) { current }
        if (previous != null && fingerprint(previous.state, previous.plan, previous.blockers) == fingerprint) return previous
        return UpdatePlanSnapshot(UUID.randomUUID(), ++revision, state, plan, blockers, null)
    }

    private fun publish(snapshot: UpdatePlanSnapshot) {
        synchronized(lock) {
            current = snapshot
            sessionHistory.addFirst(snapshot)
            while (sessionHistory.size > 100) sessionHistory.removeLast()
        }
        store.save(snapshot)
        if (!configuration.effectiveConsoleNotifications) return
        val fingerprint = fingerprint(snapshot.state, snapshot.plan, snapshot.blockers)
        val now = clock.instant()
        val repeat = announcedAt?.let { !now.isBefore(it.plus(configuration.notifications.repeatInterval)) } ?: true
        if (fingerprint != announcedFingerprint || repeat) {
            announcement(snapshot)
            announcedFingerprint = fingerprint
            announcedAt = now
        }
    }

    private fun stageInternal(snapshot: UpdatePlanSnapshot) {
        stageAction(snapshot)
        publish(UpdatePlanSnapshot(snapshot.id, snapshot.revision, UpdateState.UPDATE_STAGED, snapshot.plan, snapshot.blockers, null))
    }

    private fun fingerprint(state: UpdateState, plan: ru.privatenull.pnlibrary.api.updates.UpdatePlan?, blockers: List<*>): String =
        buildString {
            append(state.name).append('|').append(plan?.targetApi).append('|')
            plan?.changes?.sortedBy { it.product.value }?.forEach {
                append(it.product.value).append(':').append(it.from).append('>').append(it.to).append(';')
            }
            blockers.forEach { append(it.toString()).append(';') }
        }

    private fun rootMessage(error: Throwable): String =
        generateSequence(error) { it.cause }.last().message ?: error.javaClass.simpleName

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdownNow()
    }
}
