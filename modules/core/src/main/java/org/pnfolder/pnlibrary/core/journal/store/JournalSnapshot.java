package org.pnfolder.pnlibrary.core.journal.store;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;

import java.util.List;

/** What one journal read found: the newest matching records and whether the byte budget stopped it early. */
public final class JournalSnapshot {
    private final List<JournalRecord> records;
    private final boolean truncated;

    public JournalSnapshot(@NotNull List<JournalRecord> records, boolean truncated) {
        this.records = records;
        this.truncated = truncated;
    }

    public @NotNull List<JournalRecord> getRecords() {
        return records;
    }

    /** True when the read budget stopped the read before every match was seen. */
    public boolean isTruncated() {
        return truncated;
    }
}
