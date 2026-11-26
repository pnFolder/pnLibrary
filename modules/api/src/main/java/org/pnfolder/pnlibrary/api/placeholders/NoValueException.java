package org.pnfolder.pnlibrary.api.placeholders;

import org.jetbrains.annotations.NotNull;

/** Thrown inside a placeholder method when it has no value to give; the placeholder is then left unresolved. */
public final class NoValueException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Creates the exception with the message {@code No value}. */
    public NoValueException() {
        this("No value");
    }

    /** Creates the exception with {@code message}; it carries no stack trace. */
    public NoValueException(@NotNull String message) {
        super(message, null, false, false);
    }
}
