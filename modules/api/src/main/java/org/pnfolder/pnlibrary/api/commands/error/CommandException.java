package org.pnfolder.pnlibrary.api.commands.error;

import org.jetbrains.annotations.NotNull;

/**
 * Stops the command with {@link #getError() error}. The library answers it through the five levels like any other
 * error, so the command does not print anything itself. Throw it from a handler, or call
 * {@code CommandErrors.ensure}.
 */
public final class CommandException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient CommandError error;

    /** Stops the command with {@code error}. */
    public CommandException(@NotNull CommandError error) {
        super(error.getClass().getSimpleName(), null, false, false);
        this.error = error;
    }

    /** The error to answer with. */
    @NotNull
    public CommandError getError() {
        return error;
    }
}
