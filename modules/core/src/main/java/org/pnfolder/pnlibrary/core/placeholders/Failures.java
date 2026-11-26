package org.pnfolder.pnlibrary.core.placeholders;

import org.jetbrains.annotations.NotNull;

/** Turns what a placeholder, a format or an adapter threw into one readable line. */
public final class Failures {
    private Failures() {
    }

    /** The message of {@code failure}, or its class name when it has none. */
    public static @NotNull String describe(@NotNull Throwable failure) {
        String message = failure.getMessage();
        return message != null ? message : failure.getClass().getSimpleName();
    }

    /** Like {@link #describe}, but the message of the cause goes first: for failures of asynchronous stages. */
    public static @NotNull String describeWrapped(@NotNull Throwable failure) {
        Throwable cause = failure.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return describe(failure);
    }

    /** Throws {@code failure} as it is when it is unchecked, otherwise wrapped; the result is for {@code throw}. */
    public static @NotNull RuntimeException unchecked(@NotNull Throwable failure) {
        if (failure instanceof RuntimeException) {
            return (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        return new IllegalStateException(failure);
    }
}
