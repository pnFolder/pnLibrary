package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.ArrayList;
import java.util.List;

import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Runs a list of cleanup steps so that one failing step does not keep the others from running. */
final class ActionsRunner {
    private ActionsRunner() {
    }

    /** Runs every action even when some fail; throws the first failure with the rest suppressed. */
    static void runEach(List<Runnable> actions) {
        List<Throwable> failures = new ArrayList<>();
        for (Runnable action : actions) {
            Attempt<Object> result = Failures.runCatchingNonFatal(() -> {
                action.run();
                return null;
            });
            if (result.isFailure()) {
                failures.add(result.exceptionOrNull());
            }
        }
        if (failures.isEmpty()) {
            return;
        }
        Throwable first = failures.get(0);
        for (Throwable other : failures.subList(1, failures.size())) {
            first.addSuppressed(other);
        }
        if (first instanceof RuntimeException) {
            throw (RuntimeException) first;
        }
        if (first instanceof Error) {
            throw (Error) first;
        }
        throw new IllegalStateException(first);
    }
}
