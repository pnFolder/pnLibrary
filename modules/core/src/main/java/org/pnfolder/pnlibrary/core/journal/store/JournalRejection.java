package org.pnfolder.pnlibrary.core.journal.store;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

/** Told about a record the journal could not store, while the records around it are still written. */
@FunctionalInterface
public interface JournalRejection {
    void rejected(@NotNull JournalRecord record, @NotNull Throwable error);
}
