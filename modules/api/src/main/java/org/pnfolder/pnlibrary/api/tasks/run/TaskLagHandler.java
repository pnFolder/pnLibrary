package org.pnfolder.pnlibrary.api.tasks.run;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.tasks.info.TaskLag;

/**
 * Reacts when a run starts later than the task's lag threshold, for example because the server froze.
 *
 * <p>Use it to tell an administrator, to log, or to decide that a stale run is not worth doing:
 *
 * <pre>{@code
 * builder.onLag(Duration.ofSeconds(5), lag -> lag.getMissedRuns() > 10 ? LagResponse.SKIP : LagResponse.RUN);
 * }</pre>
 *
 * <p>It runs on the task's own thread right before the run. An exception it throws is logged and the run goes on.
 */
@FunctionalInterface
public interface TaskLagHandler {
    /** Decides what happens to the late run described by {@code lag}. */
    @NotNull
    LagResponse handle(@NotNull TaskLag lag);
}
