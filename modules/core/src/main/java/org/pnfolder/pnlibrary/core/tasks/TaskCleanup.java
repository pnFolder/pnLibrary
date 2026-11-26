package org.pnfolder.pnlibrary.core.tasks;

import java.util.ArrayList;
import java.util.List;
import org.pnfolder.pnlibrary.core.exceptions.CleanupStep;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;

/** Runs cleanup steps so that one failing step does not stop the rest. */
final class TaskCleanup {
    private TaskCleanup() {
    }

    /** Runs every step, then throws the first failure with the later ones attached to it. */
    static void closeAll(List<? extends Runnable> steps) {
        ResourceCleanup.closeAll(wrap(steps));
    }

    /** Runs every step; failures of the steps are attached to {@code primary}. */
    static void suppressInto(Throwable primary, Runnable... steps) {
        List<CleanupStep> wrapped = wrap(java.util.Arrays.asList(steps));
        ResourceCleanup.suppressInto(primary, wrapped.toArray(new CleanupStep[0]));
    }

    private static List<CleanupStep> wrap(List<? extends Runnable> steps) {
        List<CleanupStep> wrapped = new ArrayList<>();
        for (Runnable step : steps) {
            wrapped.add(step::run);
        }
        return wrapped;
    }
}
