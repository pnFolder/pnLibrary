package org.pnfolder.pnlibrary.core.exceptions;

import org.jetbrains.annotations.NotNull;

/** Helpers for reading the failure that actually happened out of a chain of wrapped causes. */
public final class Throwables {
    private Throwables() {
    }

    /** The innermost cause of {@code error}, or {@code error} itself when it has none. */
    @NotNull
    public static Throwable rootCause(@NotNull Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** The message of {@link #rootCause(Throwable)}, or its class name when it has no message. */
    @NotNull
    public static String rootMessage(@NotNull Throwable error) {
        Throwable root = rootCause(error);
        String message = root.getMessage();
        return message != null ? message : root.getClass().getSimpleName();
    }
}
