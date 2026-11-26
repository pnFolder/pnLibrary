package org.pnfolder.pnlibrary.core.exceptions;

import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Runs cleanup steps so that one failing step does not stop the rest. */
public final class ResourceCleanup {
    private ResourceCleanup() {
    }

    /** Runs every step; failures are attached to {@code primary} so the caller sees the original cause. */
    public static void suppressInto(@NotNull Throwable primary, @NotNull CleanupStep... steps) {
        for (CleanupStep step : steps) {
            try {
                step.run();
            } catch (Throwable cleanupFailure) {
                if (cleanupFailure != primary) {
                    primary.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    /** Runs every step, then throws the first failure with the later ones attached to it. */
    public static void closeAll(@NotNull CleanupStep... steps) {
        closeAll(Arrays.asList(steps));
    }

    /** Same as {@link #closeAll(CleanupStep...)} for a list of steps. */
    public static void closeAll(@NotNull List<? extends CleanupStep> steps) {
        Throwable failure = null;
        for (CleanupStep step : steps) {
            try {
                step.run();
            } catch (Throwable error) {
                if (failure == null) {
                    failure = error;
                } else if (error != failure) {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            sneakyThrow(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable failure) throws E {
        throw (E) failure;
    }
}
