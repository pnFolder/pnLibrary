package org.pnfolder.pnlibrary.updates.engine.transaction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Finds the saved sets that the offline installer still has to apply and the installations that belong to a set. */
final class InstallationQueries {
    private final TransactionJournalRepository journals;

    InstallationQueries(TransactionJournalRepository journals) {
        this.journals = journals;
    }

    /** Saved-set journals that were applied but not yet installed or restored. */
    List<Path> pending() throws IOException {
        List<Path> pending = new ArrayList<>();
        for (Map.Entry<Path, TransactionJournal> entry : journals.loadAll()) {
            TransactionJournal journal = entry.getValue();
            boolean applied = journal.getState() == TransactionState.AWAITING_HEALTH && !"INSTALL".equals(journal.getAppliedOperation());
            boolean restored = journal.getState() == TransactionState.ROLLED_BACK && journal.getRollbackRequested()
                && !"RESTORE".equals(journal.getAppliedOperation());
            if (journal.isSavedSet() && (applied || restored)) {
                pending.add(entry.getKey());
            }
        }
        return pending;
    }

    /** The committed journal of {@code purpose} that belongs to {@code parent}, if any. */
    Path appliedChild(UUID parent, String purpose) throws IOException {
        for (Map.Entry<Path, TransactionJournal> entry : journals.loadAll()) {
            TransactionJournal journal = entry.getValue();
            if (parent.toString().equals(journal.getParentTransaction()) && purpose.equals(journal.getPurpose())
                && journal.getState() == TransactionState.COMMITTED) {
                return entry.getKey();
            }
        }
        return null;
    }
}
