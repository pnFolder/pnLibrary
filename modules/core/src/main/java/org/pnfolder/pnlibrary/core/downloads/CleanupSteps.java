package org.pnfolder.pnlibrary.core.downloads;

import org.jetbrains.annotations.NotNull;

/** Runs cleanup steps so that one failing step does not stop the rest. */
public final class CleanupSteps {
    /** One cleanup action; it may fail with any exception. */
    @FunctionalInterface
    public interface Step {
        /** Does the cleanup. */
        void run() throws Exception;
    }

    private CleanupSteps() {
    }

    /** Runs every step; failures are attached to {@code primary} so the caller still sees the original cause. */
    public static void suppressInto(@NotNull Throwable primary, @NotNull Step... steps) {
        for (Step step : steps) {
            try {
                step.run();
            } catch (Throwable failure) {
                if (failure != primary) {
                    primary.addSuppressed(failure);
                }
            }
        }
    }

    /** Runs every step, then throws the first failure with the later ones attached to it. */
    public static void closeAll(@NotNull Step... steps) {
        Throwable first = null;
        for (Step step : steps) {
            try {
                step.run();
            } catch (Throwable failure) {
                if (first == null) {
                    first = failure;
                } else if (failure != first) {
                    first.addSuppressed(failure);
                }
            }
        }
        if (first != null) {
            throw sneaky(first);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneaky(Throwable error) throws T {
        throw (T) error;
    }
}
