package ru.privatenull.pnlibrary.spi.platform

/**
 * Holds startup-dependent work until the native entry point reports that the platform is ready.
 *
 * Tasks submitted before [markReady] are queued and released once, in submission order, through
 * [dispatch]. Later tasks are dispatched immediately. Nothing runs after [lifecycle] is closed.
 */
class StartupGate(
    private val lifecycle: PlatformLifecycle,
    private val dispatch: (Runnable) -> Unit,
) {
    private val lock = Any()
    private var ready = false
    private val pending = mutableListOf<Runnable>()

    /** Queues [task] until the gate opens, or dispatches it immediately when it is already open. */
    fun whenReady(task: Runnable) {
        synchronized(lock) {
            if (lifecycle.isClosed) return
            if (!ready) {
                pending += task
                return
            }
        }
        dispatch(task)
    }

    /** Opens the gate and releases queued work once; later calls and calls after close are ignored. */
    fun markReady() {
        val released = synchronized(lock) {
            if (ready || lifecycle.isClosed) return
            ready = true
            pending.toList().also { pending.clear() }
        }
        released.forEach(dispatch)
    }

    /** Drops queued work that has not been released yet. */
    fun discardPending() {
        synchronized(lock) { pending.clear() }
    }
}
