package org.pnfolder.pnlibrary.api.security;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.Function;

/** Owns a secret buffer and lends disposable copies for scoped access. */
public final class SecretValue implements AutoCloseable {
    private final char[] characters;
    private boolean closed;

    /**
     * Creates a secret that keeps its own copy of {@code value}.
     *
     * @param value the secret characters; the caller may erase its array afterwards
     */
    public SecretValue(@NotNull char[] value) {
        this.characters = value.clone();
    }

    /**
     * Runs {@code operation} with a borrowed copy of the secret and erases that copy afterwards, even when the
     * operation fails. Do not retain the copy.
     *
     * @throws IllegalStateException when the secret is closed
     */
    public synchronized <R> R useSecret(@NotNull Function<char[], R> operation) {
        if (closed) {
            throw new IllegalStateException("Secret value is closed");
        }
        char[] borrowed = characters.clone();
        try {
            return operation.apply(borrowed);
        } finally {
            Arrays.fill(borrowed, '\u0000');
        }
    }

    /** Erases the owned buffer and prevents subsequent access. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        Arrays.fill(characters, '\u0000');
    }

    /** Returns a redacted placeholder so the secret never reaches logs. */
    @NotNull
    @Override
    public String toString() {
        return "SecretValue([REDACTED])";
    }
}
