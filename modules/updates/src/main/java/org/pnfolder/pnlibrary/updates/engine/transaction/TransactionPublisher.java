package org.pnfolder.pnlibrary.updates.engine.transaction;

import org.pnfolder.pnlibrary.updates.engine.transaction.journal.JournalArtifact;
import org.pnfolder.pnlibrary.updates.engine.transaction.journal.TransactionJournal;
import org.pnfolder.pnlibrary.updates.engine.transaction.journal.TransactionJournalRepository;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Verifies, stages, backs up and publishes the artifacts of one journaled transaction. */
final class TransactionPublisher {
    private final TransactionJournalRepository journals;
    private final ArtifactVerifier verifier;
    private final AtomicPublication publication;
    private final TransactionRollback rollback;

    TransactionPublisher(TransactionJournalRepository journals, ArtifactVerifier verifier, AtomicPublication publication,
                         TransactionRollback rollback) {
        this.journals = journals;
        this.verifier = verifier;
        this.publication = publication;
        this.rollback = rollback;
    }

    /** Leaves the transaction awaiting restart health; a failure after publication starts restores the backups. */
    TransactionResult publish(Path journalPath, TransactionJournal journal, List<TransactionArtifact> artifacts,
                              List<JournalArtifact> records) throws IOException {
        boolean activationStarted = false;
        try {
            for (TransactionArtifact artifact : artifacts) {
                verifier.verify(artifact.getSource(), artifact.getSpecification());
            }
            journals.transition(journalPath, journal, TransactionState.VERIFIED);
            stage(artifacts, records);
            journals.transition(journalPath, journal, TransactionState.STAGED);
            backUp(artifacts, records);
            journals.transition(journalPath, journal, TransactionState.PUBLISHING);
            activationStarted = true;
            for (JournalArtifact record : records) {
                publication.replace(Paths.get(record.getStaged()), Paths.get(record.getTarget()));
            }
            journals.transition(journalPath, journal, TransactionState.AWAITING_HEALTH);
            return new TransactionResult(journal.getState(), journalPath);
        } catch (Throwable error) {
            undo(journalPath, journal, activationStarted, error);
            throw error;
        }
    }

    private void stage(List<TransactionArtifact> artifacts, List<JournalArtifact> records) throws IOException {
        for (int index = 0; index < artifacts.size(); index++) {
            TransactionArtifact artifact = artifacts.get(index);
            JournalArtifact record = records.get(index);
            Path staged = Paths.get(record.getStaged());
            Files.copy(artifact.getSource(), staged, StandardCopyOption.REPLACE_EXISTING);
            verifier.verify(staged, artifact.getSpecification().withFileName(staged.getFileName().toString()));
            if (record.getExpected() != null) {
                record.getExpected().setSha256(journals.digest(staged));
            }
        }
    }

    private void backUp(List<TransactionArtifact> artifacts, List<JournalArtifact> records) throws IOException {
        for (int index = 0; index < artifacts.size(); index++) {
            JournalArtifact record = records.get(index);
            if (!record.getTargetExisted()) {
                continue;
            }
            Path backup = Paths.get(record.getBackup());
            Files.copy(artifacts.get(index).getRollbackSource(), backup, StandardCopyOption.REPLACE_EXISTING);
            record.setBackupSha256(journals.digest(backup));
            if (record.getPrior() != null) {
                record.getPrior().setSha256(record.getBackupSha256());
            }
        }
    }

    private void undo(Path journalPath, TransactionJournal journal, boolean activationStarted, Throwable error) {
        if (activationStarted) {
            Failures.runCatchingNonFatal(() -> {
                rollback.run(journalPath, journal);
                return null;
            }).onFailure(error::addSuppressed);
        } else {
            Failures.runCatchingNonFatal(() -> {
                journals.transition(journalPath, journal, TransactionState.FAILED);
                return null;
            }).onFailure(error::addSuppressed);
        }
    }
}
