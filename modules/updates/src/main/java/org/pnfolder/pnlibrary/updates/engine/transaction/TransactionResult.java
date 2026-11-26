package org.pnfolder.pnlibrary.updates.engine.transaction;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/** Final observed transaction result. */
public final class TransactionResult {
    private final TransactionState state;
    private final Path journal;

    /**
     * @param state resulting durable lifecycle state
     * @param journal durable transaction journal path
     */
    public TransactionResult(@NotNull TransactionState state, @NotNull Path journal) {
        this.state = state;
        this.journal = journal;
    }

    @NotNull
    public TransactionState getState() {
        return state;
    }

    @NotNull
    public Path getJournal() {
        return journal;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransactionResult)) {
            return false;
        }
        TransactionResult that = (TransactionResult) other;
        return state == that.state && journal.equals(that.journal);
    }

    @Override
    public int hashCode() {
        return 31 * state.hashCode() + journal.hashCode();
    }

    @Override
    public String toString() {
        return "TransactionResult(state=" + state + ", journal=" + journal + ")";
    }
}
