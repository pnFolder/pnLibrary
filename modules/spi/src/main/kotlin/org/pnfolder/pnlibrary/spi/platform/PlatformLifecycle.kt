package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import kotlin.jvm.JvmSynthetic

/**
 * Owns the registrations created by one platform adapter.
 *
 * A new adapter is [State.READY]. [bind] enters [State.BINDING] and creates
 * commands or listeners registered through [track]. Successful binding leaves it
 * [State.BOUND]; failed binding closes tracked resources and allows another attempt.
 * [close] permanently moves either open state to [State.CLOSED].
 *
 * Mutations share one monitor, so shutdown waits for binding to finish.
 * Resources close in reverse registration order, even if individual cleanup fails.
 * Consumer plugin ownership is managed separately from this adapter lifecycle.
 *
 * Usage:
 * ```kotlin
 * lifecycle.bind {
 *     lifecycle.track(registerCommand())
 *     lifecycle.track(registerListener())
 * }
 * // During adapter shutdown:
 * lifecycle.close { detachLogObserver() }
 * ```
 */
class PlatformLifecycle {
    /** Internal states; transitions are owned exclusively by [bind] and [close]. */
    private enum class State {
        /** No successful binding yet. Initial state and destination of a failed attempt. */
        READY,

        /** Registration is running. Nested binding and shutdown from its callback are forbidden. */
        BINDING,

        /** Registration succeeded. Resources remain owned until shutdown. */
        BOUND,

        /** Shutdown started. This terminal state rejects new registrations and binding. */
        CLOSED,
    }

    @Volatile
    private var state = State.READY
    private val resources = mutableListOf<AutoCloseable>()

    /** Whether shutdown has started; this remains true even if cleanup fails. */
    val isClosed: Boolean get() = state == State.CLOSED

    /** Rejects work submitted after shutdown. */
    fun checkOpen() {
        check(!isClosed) { "Platform adapter is closed" }
    }

    /**
     * Registers adapter resources once, holding the lifecycle monitor throughout [block].
     *
     * On success: READY -> BINDING -> BOUND. On failure, tracked resources are closed
     * and the lifecycle returns to READY so binding can be retried. The original
     * registration error is rethrown with cleanup errors attached as suppressed exceptions.
     * Another thread calling [close] waits until registration completes.
     *
     * @param block registration code; pass each owned registration to [track].
     * @return the value returned by [block].
     * @throws IllegalStateException if binding already started, succeeded, or shutdown started.
     */
    @JvmSynthetic
    @Synchronized
    fun <T> bind(block: () -> T): T {
        checkOpen()
        check(state == State.READY) { "Platform adapter binding already started or completed" }
        state = State.BINDING
        return try {
            block().also { state = State.BOUND }
        } catch (error: Throwable) {
            try {
                closeTracked()?.let(error::addSuppressed)
            } finally {
                state = State.READY
            }
            throw error
        }
    }

    /**
     * Transfers cleanup ownership of [resource] to this lifecycle and returns it unchanged.
     * Resources may be tracked before, during, or after binding, until shutdown starts.
     * They are closed in reverse order on shutdown or when binding fails.
     *
     * @throws IllegalStateException if shutdown already started; ownership is not transferred.
     */
    @Synchronized
    fun <T : AutoCloseable> track(resource: T): T {
        checkOpen()
        resources += resource
        return resource
    }

    /** Shuts down once; returns false if shutdown already started. */
    @Synchronized
    fun close(): Boolean = close {}

    /**
     * Permanently closes the adapter, releasing resources before running [afterClose].
     * All cleanup actions are attempted. The first failure is thrown, with subsequent
     * failures suppressed. The adapter remains closed even if cleanup fails.
     *
     * @param afterClose final cleanup, executed only on the first shutdown attempt.
     * @return true on the first successful shutdown; false if already closed.
     * @throws IllegalStateException when called from inside the binding callback.
     */
    @Synchronized
    fun close(afterClose: () -> Unit): Boolean {
        if (isClosed) return false
        check(state != State.BINDING) { "Cannot close the platform adapter inside its binding callback" }
        state = State.CLOSED
        var failure = closeTracked()
        runCatching { afterClose() }.onFailure { cleanupFailure ->
            if (failure == null) {
                failure = cleanupFailure
            } else {
                failure?.addSuppressed(cleanupFailure)
            }
        }
        failure?.let { throw it }
        return true
    }

    /**
     * Shuts a platform adapter down in the order every platform uses: tracked registrations, then startup
     * work still waiting in [startup], then the native tasks of [tasks], and finally native log observation
     * through [stopLogObservation]. Every step is attempted; failures are reported as by [close].
     *
     * @return true on the first shutdown; false if already closed.
     */
    fun closeAdapter(startup: StartupGate?, tasks: PlatformTaskAdapter, stopLogObservation: () -> Unit): Boolean =
        close {
            try {
                startup?.discardPending()
                tasks.close()
            } finally {
                stopLogObservation()
            }
        }

    private fun closeTracked(): Throwable? {
        val failures = mutableListOf<Throwable>()
        val closing = resources.toList().asReversed()
        resources.clear()
        closing.forEach { resource ->
            runCatching { resource.close() }.onFailure(failures::add)
        }
        val first = failures.firstOrNull() ?: return null
        failures.drop(1).forEach(first::addSuppressed)
        return first
    }
}
