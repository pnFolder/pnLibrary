package org.pnfolder.pnlibrary.core.updates.service

import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdatePlan
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.core.updates.engine.ResolutionResult
import org.pnfolder.pnlibrary.core.updates.history.UpdateStateStore
import org.pnfolder.pnlibrary.core.updates.policy.UpdateConfiguration
import java.time.Clock
import java.time.Instant
import java.util.Collections
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Runs update checks on a schedule or on request, keeps the current plan and stages automatic updates. */
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
    private val observeState: (UpdatePlanSnapshot, UpdateActor, String) -> Unit = { _, _, _ -> },
    private val beforeCheck: () -> Unit = {},
    private val automaticOverridesActive: () -> Boolean = { false },
) : AutoCloseable {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<UpdatePlanSnapshot>>()

    private fun operationPromise(): CompletableFuture<UpdatePlanSnapshot> {
        val promise = CompletableFuture<UpdatePlanSnapshot>()
        pending.add(promise)
        promise.whenComplete { _, _ -> pending.remove(promise) }
        // Recheck after registration so concurrent close cannot miss this request.
        if (closed.get()) promise.completeExceptionally(IllegalStateException("Update orchestrator is closed"))
        return promise
    }
    private var inFlight: CompletableFuture<UpdatePlanSnapshot>? = null
    private var inFlightForced = false
    private var current: UpdatePlanSnapshot? = store.current()
    private var revision = current?.revision ?: 0
    private var announcedFingerprint: String? = null
    private var announcedAt: Instant? = null
    private val sessionHistory = ArrayDeque<UpdatePlanSnapshot>()

    fun start() {
        if (!configuration.effectiveChecksEnabled || closed.get() || !started.compareAndSet(false, true)) return
        executor.scheduleWithFixedDelay(
            { checkNow(false, UpdateActor.SYSTEM).exceptionally { null } },
            0,
            configuration.checks.interval.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    fun checkNow(forceRemote: Boolean = true, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<UpdatePlanSnapshot> = synchronized(lock) {
        check(!closed.get()) { "Update orchestrator is closed" }
        inFlight?.let { active ->
            if (!forceRemote || inFlightForced) return@synchronized active
            return@synchronized active.handle { _, _ -> Unit }.thenCompose { checkNow(true, actor) }
        }
        val promise = operationPromise()
        inFlight = promise
        inFlightForced = forceRemote
        try {
            executor.execute { performCheck(promise, forceRemote, actor) }
        } catch (error: Throwable) {
            releaseInFlight(promise)
            promise.completeExceptionally(error)
        }
        promise
    }

    private fun performCheck(promise: CompletableFuture<UpdatePlanSnapshot>, forceRemote: Boolean, actor: UpdateActor) {
        if (promise.isDone) { releaseInFlight(promise); return }
        try {
            val completed = runCatching {
                beforeCheck()
                val checked = snapshot(resolve(forceRemote))
                check(!closed.get()) { "Update orchestrator is closed" }
                publish(checked, actor, "CHECK")
                automaticallyStageIfAllowed(checked)
            }.getOrElse { error ->
                if (closed.get()) throw error
                failedSnapshot(error).also { publish(it, actor, "CHECK") }
            }
            releaseInFlight(promise)
            promise.complete(completed)
        } catch (error: Throwable) {
            promise.completeExceptionally(error)
        } finally {
            releaseInFlight(promise)
        }
    }

    private fun resolve(forceRemote: Boolean): ResolutionResult =
        if (forceRemote) remoteResolver?.invoke() ?: resolver() else resolver()

    private fun automaticallyStageIfAllowed(snapshot: UpdatePlanSnapshot): UpdatePlanSnapshot {
        val shouldStage = (configuration.effectiveAutomaticDownloads || automaticOverridesActive()) &&
            snapshot.state == UpdateState.UPDATE_AVAILABLE && automaticAllowed(snapshot)
        if (!shouldStage) return snapshot
        try {
            stageInternal(snapshot, UpdateActor.SYSTEM)
        } catch (error: Throwable) {
            observeFailure(snapshot.id, UpdateActor.SYSTEM, "STAGE", error)
            throw error
        }
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
        if (closed.get() || !started.get() || !configuration.effectiveChecksEnabled) return
        try {
            executor.execute { if (!closed.get()) checkNow(false, UpdateActor.SYSTEM) }
        } catch (_: RejectedExecutionException) {
            // A concurrent executor shutdown is a normal lifecycle race. The owner
            // will close the orchestrator and no follow-up check is required.
        }
    }

    fun stage(planId: UUID, actor: UpdateActor = UpdateActor.UNKNOWN): CompletionStage<UpdatePlanSnapshot> {
        val promise = operationPromise()
        if (promise.isDone) return promise
        try {
            executor.execute {
                if (promise.isDone) return@execute
                try {
                    val selected = synchronized(lock) { current }
                    require(selected != null && selected.id == planId) { "Stale or unknown update plan: $planId" }
                    require(selected.state == UpdateState.UPDATE_AVAILABLE) { "Update plan is not available for staging" }
                    stageInternal(selected, actor)
                    promise.complete(requireNotNull(current))
                } catch (error: Throwable) {
                    observeFailure(planId, actor, "STAGE", error)
                    promise.completeExceptionally(error)
                }
            }
        } catch (error: Throwable) {
            promise.completeExceptionally(error)
        }
        return promise
    }

    fun history(): List<UpdatePlanSnapshot> = synchronized(lock) {
        Collections.unmodifiableList(ArrayList(sessionHistory))
    }

    fun rollback(actor: UpdateActor = UpdateActor.UNKNOWN, action: () -> Unit): CompletionStage<UpdatePlanSnapshot> = rollbackTo(actor) {
        val selected = synchronized(lock) { current } ?: error("No update plan is available for rollback")
        action()
        UpdatePlanSnapshot(selected.id, selected.revision, UpdateState.ROLLED_BACK, selected.plan, selected.blockers,
            "Предыдущие версии JAR подготовлены к следующему перезапуску.")
    }

    fun rollbackTo(actor: UpdateActor, failureId: UUID? = null, action: () -> UpdatePlanSnapshot): CompletionStage<UpdatePlanSnapshot> {
        val promise = operationPromise()
        if (promise.isDone) return promise
        try {
            executor.execute {
                if (promise.isDone) return@execute
                try {
                    val selected = action()
                    val restored = UpdatePlanSnapshot(
                        selected.id,
                        ++revision,
                        UpdateState.ROLLED_BACK,
                        selected.plan,
                        selected.blockers,
                        selected.message,
                    )
                    publish(restored, actor, "ROLLBACK")
                    promise.complete(restored)
                } catch (error: Throwable) {
                    val planId = failureId ?: synchronized(lock) { current?.id } ?: UUID.randomUUID()
                    observeFailure(planId, actor, "ROLLBACK", error)
                    promise.completeExceptionally(error)
                }
            }
        } catch (error: Throwable) {
            promise.completeExceptionally(error)
        }
        return promise
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

    private fun publish(snapshot: UpdatePlanSnapshot, actor: UpdateActor = UpdateActor.UNKNOWN, operation: String = "STATE_OBSERVED") {
        synchronized(lock) {
            current = snapshot
            sessionHistory.addFirst(snapshot)
            while (sessionHistory.size > 100) sessionHistory.removeLast()
        }
        store.save(snapshot)
        observeState(snapshot, actor, operation)
        if (closed.get() || !configuration.effectiveConsoleNotifications) return
        val fingerprint = fingerprint(snapshot.state, snapshot.plan, snapshot.blockers)
        val now = clock.instant()
        val repeat = announcedAt?.let { !now.isBefore(it.plus(configuration.notifications.repeatInterval)) } ?: true
        if (fingerprint != announcedFingerprint || repeat) {
            announcement(snapshot)
            announcedFingerprint = fingerprint
            announcedAt = now
        }
    }

    private fun observeFailure(planId: UUID, actor: UpdateActor, operation: String, error: Throwable) {
        val selected = synchronized(lock) { current?.takeIf { it.id == planId } }
        // A rejected operation is an audit event, not a replacement for the current valid plan.
        runCatching {
            observeState(UpdatePlanSnapshot(
                planId, selected?.revision ?: 0, UpdateState.FAILED,
                selected?.plan, selected?.blockers.orEmpty(), rootMessage(error),
            ), actor, operation)
        }.onFailure { if (it !== error) error.addSuppressed(it) }
    }

    private fun stageInternal(snapshot: UpdatePlanSnapshot, actor: UpdateActor) {
        check(!closed.get()) { "Update orchestrator is closed" }
        stageAction(snapshot)
        publish(UpdatePlanSnapshot(snapshot.id, snapshot.revision, UpdateState.UPDATE_STAGED, snapshot.plan, snapshot.blockers, null), actor, "STAGE")
    }

    private fun fingerprint(state: UpdateState, plan: UpdatePlan?, blockers: List<*>): String =
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
        pending.forEach { it.completeExceptionally(IllegalStateException("Update orchestrator is closed")) }
        executor.shutdownNow()
    }
}
