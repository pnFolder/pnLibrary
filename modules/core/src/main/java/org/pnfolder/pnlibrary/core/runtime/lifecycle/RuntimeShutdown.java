package org.pnfolder.pnlibrary.core.runtime.lifecycle;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.exceptions.CleanupStep;

/** Ordered list of shutdown actions; all run even if some fail. */
public final class RuntimeShutdown {
    private final List<CleanupStep> actions = new ArrayList<>();

    public void add(@NotNull CleanupStep action) {
        actions.add(action);
    }

    public void run() {
        List<Throwable> failures = new ArrayList<>();
        for (CleanupStep action : actions) {
            try {
                action.run();
            } catch (Throwable error) {
                Failures.rethrowIfFatal(error);
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                failures.add(error);
            }
        }
        if (failures.isEmpty()) {
            return;
        }
        Throwable first = failures.get(0);
        for (Throwable other : failures.subList(1, failures.size())) {
            first.addSuppressed(other);
        }
        sneakyThrow(first);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable error) throws E {
        throw (E) error;
    }
}
