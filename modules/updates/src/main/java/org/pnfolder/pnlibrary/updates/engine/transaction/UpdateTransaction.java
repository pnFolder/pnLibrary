package org.pnfolder.pnlibrary.updates.engine.transaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.updates.engine.catalog.RollbackCatalog;
import org.pnfolder.pnlibrary.updates.engine.net.ArtifactVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Coordinates verified staging, atomic publication, health confirmation, and rollback. */
public final class UpdateTransaction {
    private final Path root;
    private final Path publicationRoot;
    private final TransactionJournalRepository journals;
    private final TransactionHealth health;
    private final RollbackCatalog rollbacks;
    private final TransactionPublisher publisher;
    private final TransactionRollback restorer;
    private final InstallationQueries installations;

    public UpdateTransaction(@NotNull Path root, @NotNull ArtifactVerifier verifier) {
        this(root, verifier, root.toAbsolutePath().normalize().getParent());
    }

    public UpdateTransaction(@NotNull Path root, @NotNull ArtifactVerifier verifier, @NotNull Path publicationRoot) {
        this(root, verifier, publicationRoot, message -> {
        });
    }

    /**
     * @param root folder holding one directory per transaction
     * @param publicationRoot folder every published file must stay inside
     * @param warn receives a message for each unreadable journal that is skipped
     */
    public UpdateTransaction(@NotNull Path root, @NotNull ArtifactVerifier verifier, @NotNull Path publicationRoot,
                             @NotNull Consumer<String> warn) {
        this.root = root;
        this.publicationRoot = publicationRoot.toAbsolutePath().normalize();
        this.journals = new TransactionJournalRepository(root, this.publicationRoot, warn);
        this.health = new TransactionHealth(root, journals);
        this.rollbacks = new RollbackCatalog(root, journals);
        this.installations = new InstallationQueries(journals);
        AtomicPublication publication = new AtomicPublication();
        this.restorer = new TransactionRollback(journals, publication);
        this.publisher = new TransactionPublisher(journals, verifier, publication, restorer);
    }

    /** A health check that decides whether a prepared transaction commits. */
    @FunctionalInterface
    public interface HealthCheck {
        boolean isHealthy();
    }

    /** Prepares {@code artifacts}, evaluates {@code healthCheck}, and completes the transaction. */
    @NotNull
    public TransactionResult apply(@NotNull List<TransactionArtifact> artifacts, @NotNull HealthCheck healthCheck)
        throws IOException {
        TransactionResult prepared = prepareForRestart(artifacts);
        return completeHealth(prepared.getJournal(), healthCheck.isHealthy());
    }

    /** Verifies and publishes {@code artifacts}, leaving the transaction awaiting restart health. */
    @NotNull
    public TransactionResult prepareForRestart(@NotNull List<TransactionArtifact> artifacts) throws IOException {
        return prepareForRestart(artifacts, null, null, null);
    }

    @NotNull
    public TransactionResult prepareForRestart(@NotNull List<TransactionArtifact> artifacts, @Nullable UUID planId,
                                               @Nullable Integer targetApi, @Nullable UUID configurationSnapshot)
        throws IOException {
        return prepareForRestart(artifacts, planId, targetApi, configurationSnapshot, "STAGE", null);
    }

    /**
     * Verifies and publishes {@code artifacts}, leaving the transaction awaiting restart health.
     *
     * @param purpose {@code STAGE} for a saved set, {@code INSTALL} or {@code RESTORE} for an internal installation
     * @param parentTransaction the saved set an internal installation belongs to
     */
    @NotNull
    public TransactionResult prepareForRestart(@NotNull List<TransactionArtifact> artifacts, @Nullable UUID planId,
                                               @Nullable Integer targetApi, @Nullable UUID configurationSnapshot,
                                               @NotNull String purpose, @Nullable UUID parentTransaction)
        throws IOException {
        JournalRecords.requirePreparable(artifacts, purpose, targetApi, publicationRoot, journals);
        String id = UUID.randomUUID().toString();
        Path directory = root.toAbsolutePath().normalize().resolve(id);
        Path journalPath = directory.resolve("journal.json");
        journals.checkedPath(journalPath, root);
        Path staging = directory.resolve("staging");
        Path backups = directory.resolve("backup");
        Files.createDirectories(staging);
        Files.createDirectories(backups);
        List<JournalArtifact> records = JournalRecords.create(artifacts, staging, backups);
        TransactionJournal journal = new TransactionJournal(id, TransactionState.CREATED, records,
            planId == null ? null : planId.toString(), targetApi,
            configurationSnapshot == null ? null : configurationSnapshot.toString(), purpose,
            parentTransaction == null ? null : parentTransaction.toString());
        journals.save(journalPath, journal);
        return publisher.publish(journalPath, journal, artifacts, records);
    }

    /** Commits or fails the transaction at {@code journalPath} according to {@code healthy}. */
    @NotNull
    public TransactionResult completeHealth(@NotNull Path journalPath, boolean healthy) throws IOException {
        return health.complete(journalPath, healthy);
    }

    /** Returns transactions waiting for post-restart product-version confirmation. */
    @NotNull
    public List<PendingTransaction> awaitingHealth() throws IOException {
        return health.awaiting();
    }

    /** Rollbacks that were applied and still wait for a health check. */
    @NotNull
    public List<PendingTransaction> awaitingRollbackHealth() throws IOException {
        return health.awaitingRollback();
    }

    /** Records the health result of the rollback journal at {@code path}. */
    public void completeRollbackHealth(@NotNull Path path, boolean healthy) throws IOException {
        health.completeRollback(path, healthy);
    }

    /** Whether no unhealthy update transaction involves {@code products}. */
    public boolean automaticUpdatesHealthy(@NotNull Set<String> products) throws IOException {
        return health.automaticUpdatesHealthy(products);
    }

    /** Transactions whose health check failed. */
    @NotNull
    public List<PendingTransaction> failedHealthChecks() throws IOException {
        return health.failed();
    }

    /** Recovers an interrupted transaction, rolling back unsafe publication states. */
    @NotNull
    public TransactionResult recover(@NotNull Path journalPath) throws IOException {
        TransactionJournal journal = journals.load(journalPath);
        if (ServerStartGuard.isInterruptedInstallation(journal) || ServerStartGuard.UNSAFE_STATES.contains(journal.getState())) {
            restorer.run(journalPath, journal);
        }
        return new TransactionResult(journal.getState(), journalPath);
    }

    /** Restores artifacts recorded by the completed or failed transaction; configurations stay as they are. */
    @NotNull
    public TransactionResult rollback(@NotNull Path journalPath) throws IOException {
        return rollback(journalPath, false);
    }

    /** Restores artifacts recorded by the completed or failed transaction, and optionally their configurations. */
    @NotNull
    public TransactionResult rollback(@NotNull Path journalPath, boolean restoreConfigurations) throws IOException {
        Path normalized = journalPath.toAbsolutePath().normalize();
        if (!normalized.startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Rollback journal must stay inside transaction root");
        }
        TransactionJournal journal = journals.load(normalized);
        requireRollbackable(journal, restoreConfigurations);
        journal.setRollbackRequested(true);
        journal.setAppliedOperation(null);
        journal.setRestoreConfigurations(restoreConfigurations);
        journal.setRollbackSession(UpdateProcessSession.getId());
        journal.setRollbackHealthVerified(false);
        restorer.run(normalized, journal);
        return new TransactionResult(journal.getState(), normalized);
    }

    /** Returns the newest completed transaction that still has rollback data. */
    @Nullable
    public Path latestRollbackCandidate() throws IOException {
        return rollbacks.latest();
    }

    /** Resolves exactly one saved transaction; never substitutes the latest one. */
    @NotNull
    public Path rollbackCandidate(@NotNull UUID id) throws IOException {
        return rollbacks.resolve(id);
    }

    /** Lists validated saved rollback sets without exposing incomplete or corrupt transactions. */
    @NotNull
    public List<Path> rollbackCandidates() throws IOException {
        return rollbacks.all();
    }

    /**
     * Recovers the saved sets at server start. Refuses to start while a set that touched the loaded plugin JARs is
     * interrupted: only the offline installer may repair those, because the server may already have loaded them.
     */
    @NotNull
    public List<TransactionResult> recoverAll() throws IOException {
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }
        List<Map.Entry<Path, TransactionJournal>> readable = journals.loadAll();
        for (Map.Entry<Path, TransactionJournal> entry : readable) {
            if (ServerStartGuard.blocksServerStart(entry.getValue(), publicationRoot)) {
                throw new IllegalArgumentException("interrupted offline installation must be recovered before starting the server");
            }
        }
        List<Path> saved = new ArrayList<Path>();
        for (Map.Entry<Path, TransactionJournal> entry : readable) {
            if (entry.getValue().isSavedSet()) {
                saved.add(entry.getKey());
            }
        }
        return recoverSorted(saved);
    }

    /** Recovers every journal, internal installations included; called only by the offline installer. */
    @NotNull
    public List<TransactionResult> recoverAllOffline() throws IOException {
        if (!Files.isDirectory(root)) {
            return Collections.emptyList();
        }
        List<Path> all = new ArrayList<Path>();
        for (Map.Entry<Path, TransactionJournal> entry : journals.loadAll()) {
            all.add(entry.getKey());
        }
        return recoverSorted(all);
    }

    /** Loads the journal at {@code path}. */
    @NotNull
    public TransactionJournal readJournal(@NotNull Path path) throws IOException {
        return journals.load(path);
    }

    /** Saved-set journals that were applied but not yet installed or restored. */
    @NotNull
    public List<Path> pendingInstallations() throws IOException {
        return installations.pending();
    }

    /** The committed journal of {@code purpose} that belongs to {@code parent}, if any. */
    @Nullable
    public Path appliedChildJournal(@NotNull UUID parent, @NotNull String purpose) throws IOException {
        return installations.appliedChild(parent, purpose);
    }

    /** Rolls back an internal installation transaction. */
    public void revertInstallation(@NotNull Path path) throws IOException {
        TransactionJournal journal = journals.load(path);
        if (!ServerStartGuard.INTERNAL_PURPOSES.contains(journal.getPurpose())) {
            throw new IllegalArgumentException("Not an internal installation transaction");
        }
        restorer.run(path, journal);
    }

    /** Remembers that {@code operation} was applied for the journal at {@code path}. */
    public void markInstalled(@NotNull Path path, @NotNull String operation) throws IOException {
        if (!ServerStartGuard.INTERNAL_PURPOSES.contains(operation)) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        TransactionJournal journal = journals.load(path);
        journal.setAppliedOperation(operation);
        journals.save(path, journal);
    }

    private List<TransactionResult> recoverSorted(List<Path> paths) throws IOException {
        Collections.sort(paths);
        List<TransactionResult> results = new ArrayList<TransactionResult>();
        for (Path path : paths) {
            results.add(recover(path));
        }
        return results;
    }

    private void requireRollbackable(TransactionJournal journal, boolean restoreConfigurations) throws IOException {
        if (!journal.isRestorable()) {
            throw new IllegalArgumentException("Transaction " + journal.getId() + " cannot be rolled back from " + journal.getState());
        }
        if (!journals.hasRollbackData(journal)) {
            throw new IllegalArgumentException("Transaction " + journal.getId() + " has no complete rollback data");
        }
        if (!journal.isSavedSet()) {
            throw new IllegalArgumentException("Internal installation transactions cannot be selected for rollback");
        }
        if (restoreConfigurations && journal.getConfigurationSnapshot() == null) {
            throw new IllegalArgumentException("Transaction has no configuration snapshot");
        }
    }
}
