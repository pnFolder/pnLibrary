package org.pnfolder.pnlibrary.api.tasks

import org.jetbrains.annotations.ApiStatus
import org.pnfolder.pnlibrary.api.tasks.info.TaskId
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus

/**
 * Cancellation handle for one scheduled action.
 *
 * Cancellation is idempotent. It prevents a pending invocation from starting, but does not
 * interrupt an invocation that is already running. Closing the handle is equivalent to [cancel].
 */
@ApiStatus.NonExtendable
interface TaskHandle : AutoCloseable {
    /** Stable identifier of the associated task. */
    val id: TaskId
    /** Current task lifecycle state. */
    val status: TaskStatus
    /** Whether this handle has been cancelled. */
    val isCancelled: Boolean

    /** Cancels future execution of the associated action. */
    fun cancel()

    /** Cancels this task when active and reports whether cancellation was requested. */
    fun cancelIfActive(): Boolean {
        if (isCancelled) return false
        cancel()
        return true
    }

    /** Returns a detached snapshot of this task's current state. */
    fun snapshot(): TaskSnapshot

    /** Cancels future execution of the associated action. */
    override fun close() = cancel()
}
