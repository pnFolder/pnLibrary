package org.pnfolder.pnlibrary.updates.engine.transaction;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Post-restart health bookkeeping for staged updates and their rollbacks. */
public final class TransactionHealth {
    private final Path root;
    private final TransactionJournalRepository journals;

    public TransactionHealth(@NotNull Path root, @NotNull TransactionJournalRepository journals) {
        this.root = root;
        this.journals = journals;
    }

    /** Commits or fails the transaction at {@code journalPath} according to {@code healthy}. */
    @NotNull
    public TransactionResult complete(@NotNull Path journalPath, boolean healthy) throws IOException {
        Path normalized = journalPath.toAbsolutePath().normalize();
        if (!normalized.startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Health journal must stay inside transaction root");
        }
        TransactionJournal journal = journals.load(normalized);
        boolean awaiting = journal.getState() == TransactionState.AWAITING_HEALTH
            || (journal.getState() == TransactionState.FAILED && journal.getHealthFailure() && !journal.getRollbackRequested());
        if (!awaiting) {
            throw new IllegalArgumentException("Transaction " + journal.getId() + " is not awaiting a health check");
        }
        journal.setHealthCheckedAt(Instant.now().toString());
        journal.setHealthFailure(!healthy);
        journals.transition(normalized, journal, healthy ? TransactionState.COMMITTED : TransactionState.FAILED);
        return new TransactionResult(journal.getState(), normalized);
    }

    /** Returns transactions waiting for post-restart product-version confirmation. */
    @NotNull
    public List<PendingTransaction> awaiting() throws IOException {
        List<PendingTransaction> pending = new ArrayList<PendingTransaction>();
        for (Map.Entry<Path, TransactionJournal> entry : journals.loadAll()) {
            TransactionJournal journal = entry.getValue();
            if (journal.getState() != TransactionState.AWAITING_HEALTH || !journal.isSavedSet()) {
                continue;
            }
            Map<String, String> versions = new LinkedHashMap<String, String>();
            Set<String> unknown = new LinkedHashSet<String>();
            for (JournalArtifact artifact : journal.getArtifacts()) {
                if (artifact.getExpectedVersion() == null) {
                    unknown.add(artifact.getComponent());
                } else {
                    versions.put(artifact.getComponent(), artifact.getExpectedVersion());
                }
            }
            pending.add(new PendingTransaction(entry.getKey(), versions, journal.getPreparedSession(), unknown));
        }
        return pending;
    }

    /** Returns requested rollbacks whose restored versions are not yet confirmed. */
    @NotNull
    public List<PendingTransaction> awaitingRollback() throws IOException {
        List<PendingTransaction> pending = new ArrayList<PendingTransaction>();
        for (Map.Entry<Path, TransactionJournal> entry : journals.loadAll()) {
            TransactionJournal journal = entry.getValue();
            if (!journal.isSavedSet() || journal.getState() != TransactionState.ROLLED_BACK
                || !journal.getRollbackRequested() || journal.getRollbackHealthVerified()) {
                continue;
            }
            Map<String, String> versions = new LinkedHashMap<String, String>();
            Set<String> unknown = new LinkedHashSet<String>();
            for (JournalArtifact artifact : journal.getArtifacts()) {
                if (artifact.getPreviousVersion() == null) {
                    unknown.add(artifact.getComponent());
                } else {
                    versions.put(artifact.getComponent(), artifact.getPreviousVersion());
                }
            }
            pending.add(new PendingTransaction(entry.getKey(), versions, journal.getRollbackSession(), unknown, true));
        }
        return pending;
    }

    /** Records the post-restart health outcome of the rollback at {@code path}. */
    public void completeRollback(@NotNull Path path, boolean healthy) throws IOException {
        TransactionJournal journal = journals.load(path);
        boolean verifiable = journal.getRollbackRequested() && (journal.getState() == TransactionState.ROLLED_BACK
            || (journal.getState() == TransactionState.FAILED && journal.getHealthFailure()));
        if (!verifiable) {
            throw new IllegalArgumentException("No rollback awaits verification");
        }
        journal.setRollbackHealthVerified(true);
        journal.setHealthCheckedAt(Instant.now().toString());
        journal.setHealthFailure(!healthy);
        if (healthy) {
            journal.setAppliedOperation("RESTORE");
            journal.setState(TransactionState.ROLLED_BACK);
        } else {
            journal.setState(TransactionState.FAILED);
        }
        journals.save(path, journal);
    }

    /** Returns whether the latest health check of every product in {@code products} succeeded. */
    public boolean automaticUpdatesHealthy(@NotNull Set<String> products) throws IOException {
        Map<String, Instant> checked = new HashMap<String, Instant>();
        Map<String, Boolean> failed = new HashMap<String, Boolean>();
        for (Map.Entry<Path, TransactionJournal> entry : journals.loadAll()) {
            TransactionJournal journal = entry.getValue();
            if (!journal.isSavedSet() || journal.getHealthCheckedAt() == null) {
                continue;
            }
            Instant checkedAt = Instant.parse(journal.getHealthCheckedAt());
            for (JournalArtifact artifact : journal.getArtifacts()) {
                String component = artifact.getComponent();
                if (!products.contains(component)) {
                    continue;
                }
                Instant previous = checked.get(component);
                boolean newer = previous == null || checkedAt.compareTo(previous) > 0
                    || (checkedAt.equals(previous) && journal.getHealthFailure());
                if (newer) {
                    checked.put(component, checkedAt);
                    failed.put(component, journal.getHealthFailure());
                }
            }
        }
        return !failed.containsValue(Boolean.TRUE);
    }

    /** Returns saved component sets whose last health check failed. */
    @NotNull
    public List<PendingTransaction> failed() throws IOException {
        List<PendingTransaction> failures = new ArrayList<PendingTransaction>();
        for (Map.Entry<Path, TransactionJournal> entry : journals.loadAll()) {
            TransactionJournal journal = entry.getValue();
            if (!journal.isSavedSet() || journal.getState() != TransactionState.FAILED || !journal.getHealthFailure()) {
                continue;
            }
            Map<String, String> versions = new LinkedHashMap<String, String>();
            Set<String> unknown = new LinkedHashSet<String>();
            for (JournalArtifact artifact : journal.getArtifacts()) {
                String version = journal.getRollbackRequested() ? artifact.getPreviousVersion() : artifact.getExpectedVersion();
                if (version == null) {
                    unknown.add(artifact.getComponent());
                    versions.remove(artifact.getComponent());
                } else {
                    versions.put(artifact.getComponent(), version);
                    unknown.remove(artifact.getComponent());
                }
            }
            failures.add(new PendingTransaction(entry.getKey(), versions, null, unknown, journal.getRollbackRequested()));
        }
        return failures;
    }
}
