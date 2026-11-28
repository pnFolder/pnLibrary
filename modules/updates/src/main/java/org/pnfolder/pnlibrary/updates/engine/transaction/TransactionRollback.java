package org.pnfolder.pnlibrary.updates.engine.transaction;

import org.pnfolder.pnlibrary.updates.engine.transaction.journal.JournalArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.journal.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.journal.TransactionJournalRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Puts the preserved backups of a journaled transaction back in place. */
final class TransactionRollback {
    private final TransactionJournalRepository journals;
    private final AtomicPublication publication;

    TransactionRollback(TransactionJournalRepository journals, AtomicPublication publication) {
        this.journals = journals;
        this.publication = publication;
    }

    void run(Path path, TransactionJournal journal) throws IOException {
        // Validate the whole set before changing any target.
        journals.validate(path, journal);
        if (!journals.hasRollbackData(journal)) {
            throw new IllegalArgumentException("Transaction " + journal.getId() + " has no complete rollback data");
        }
        journals.transition(path, journal, TransactionState.ROLLING_BACK);
        for (JournalArtifact record : journal.getArtifacts()) {
            restore(record);
        }
        journals.transition(path, journal, TransactionState.ROLLED_BACK);
    }

    private void restore(JournalArtifact record) throws IOException {
        Path target = Paths.get(record.getTarget());
        if (record.getTargetExisted()) {
            publication.replace(Paths.get(record.getBackup()), target);
            return;
        }
        publication.retryWhileLocked(() -> Files.deleteIfExists(target));
        // A crash during publication can leave the copy that was about to be swapped in.
        Files.deleteIfExists(publication.publishingCopy(target));
    }
}
