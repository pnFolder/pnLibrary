package org.pnfolder.pnlibrary.common.lang;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/** Which failures a boundary that shields callers from broken plugins or platforms may swallow. */
public final class Failures {
    private Failures() {
    }

    /**
     * Rethrows {@code error} when the JVM itself is failing (out of memory, stack overflow, internal error); that is
     * not ours to hide. Linkage errors such as {@code NoClassDefFoundError} or {@code NoSuchMethodError} are not fatal:
     * they are how an incompatible platform bridge or plugin shows up, and callers fall back or log instead.
     */
    public static void rethrowIfFatal(@NotNull Throwable error) {
        if (error instanceof VirtualMachineError) {
            throw (VirtualMachineError) error;
        }
    }

    /**
     * Runs {@code block} and returns its outcome. A failing JVM is rethrown, and an interrupt is not lost: the interrupt
     * flag is restored before the failure is returned.
     *
     * <pre>{@code
     * Attempt<String> name = Failures.runCatchingNonFatal(() -> read(path));
     * String value = name.getOrDefault("unknown");
     * }</pre>
     */
    @NotNull
    public static <T> Attempt<T> runCatchingNonFatal(@NotNull Callable<T> block) {
        try {
            return Attempt.success(block.call());
        } catch (Throwable error) {
            rethrowIfFatal(error);
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Attempt.failure(error);
        }
    }
}
