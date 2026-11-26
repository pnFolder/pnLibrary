package org.pnfolder.pnlibrary.config;

import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** Runs a list of steps so that one failing step does not stop the others. */
final class ConfigRunAll {
    private ConfigRunAll() {
    }

    /** Runs every step; see {@link #runAll(List)}. */
    static void runAll(@NotNull Runnable... steps) {
        runAll(Arrays.asList(steps));
    }

    /** Runs every step even when earlier ones fail; the first failure is rethrown with later ones suppressed into it. */
    static void runAll(@NotNull List<? extends Runnable> steps) {
        Throwable failure = null;
        for (Runnable step : steps) {
            try {
                step.run();
            } catch (Throwable error) {
                Failures.rethrowIfFatal(error);
                if (failure == null) {
                    failure = error;
                } else if (error != failure) {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException(failure);
    }
}
