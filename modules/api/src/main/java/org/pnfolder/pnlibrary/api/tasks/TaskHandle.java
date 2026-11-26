package org.pnfolder.pnlibrary.api.tasks;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;

/**
 * Cancellation handle for one scheduled action.
 *
 * <p>Cancellation is idempotent. It prevents a pending invocation from starting, but does not interrupt an
 * invocation that is already running. Closing the handle is equivalent to {@link #cancel()}.
 */
@ApiStatus.NonExtendable
public interface TaskHandle extends AutoCloseable {
    /** Stable identifier of the associated task. */
    @NotNull
    TaskId getId();

    /** Current task lifecycle state. */
    @NotNull
    TaskStatus getStatus();

    /** Whether this handle has been cancelled. */
    boolean isCancelled();

    /** Cancels future execution of the associated action. */
    void cancel();

    /** Cancels this task when active and reports whether cancellation was requested. */
    default boolean cancelIfActive() {
        if (isCancelled()) {
            return false;
        }
        cancel();
        return true;
    }

    /** Returns a detached snapshot of this task's current state. */
    @NotNull
    TaskSnapshot snapshot();

    /** Cancels future execution of the associated action. */
    @Override
    default void close() {
        cancel();
    }
}
