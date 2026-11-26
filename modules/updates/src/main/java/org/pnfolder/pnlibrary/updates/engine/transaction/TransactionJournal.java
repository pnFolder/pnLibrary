package org.pnfolder.pnlibrary.updates.engine.transaction;

import java.util.Objects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable transaction state used for crash recovery and rollback. Gson reads and writes the fields directly, so their
 * names and order are the on-disk journal format.
 */
public final class TransactionJournal {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String id;
    private TransactionState state;
    private final List<JournalArtifact> artifacts;
    private final String createdAt;
    private final String planId;
    private final Integer targetApi;
    private final String configurationSnapshot;
    private final String purpose;
    private final String parentTransaction;
    private final String preparedSession;
    private boolean rollbackRequested;
    private String appliedOperation;
    private boolean restoreConfigurations;
    private String rollbackSession;
    private boolean rollbackHealthVerified;
    private String healthCheckedAt;
    private boolean healthFailure;

    /** A journal of a saved component set created now by this process. */
    public TransactionJournal(@NotNull String id, @NotNull TransactionState state, @NotNull List<JournalArtifact> artifacts) {
        this(id, state, artifacts, null, null, null, "STAGE", null);
    }

    /**
     * A journal created now by this process.
     *
     * @param id unique transaction identifier
     * @param state current durable lifecycle state
     * @param artifacts artifact records participating in this transaction
     * @param planId originating compatibility plan; null for older journals
     * @param targetApi API generation of the original plan; null for older journals
     * @param configurationSnapshot optional immutable configuration snapshot identity
     * @param purpose {@code STAGE}, {@code INSTALL} or {@code RESTORE}; null for older journals
     * @param parentTransaction the saved set an internal installation belongs to
     */
    public TransactionJournal(@NotNull String id, @NotNull TransactionState state, @NotNull List<JournalArtifact> artifacts,
                              @Nullable String planId, @Nullable Integer targetApi, @Nullable String configurationSnapshot,
                              @Nullable String purpose, @Nullable String parentTransaction) {
        this.id = Objects.requireNonNull(id, "id");
        this.state = Objects.requireNonNull(state, "state");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.createdAt = Instant.now().toString();
        this.planId = planId;
        this.targetApi = targetApi;
        this.configurationSnapshot = configurationSnapshot;
        this.purpose = purpose;
        this.parentTransaction = parentTransaction;
        this.preparedSession = UpdateProcessSession.getId();
    }

    private TransactionJournal(TransactionJournal source, List<JournalArtifact> artifacts, String preparedSession) {
        this.id = source.id;
        this.state = source.state;
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.createdAt = source.createdAt;
        this.planId = source.planId;
        this.targetApi = source.targetApi;
        this.configurationSnapshot = source.configurationSnapshot;
        this.purpose = source.purpose;
        this.parentTransaction = source.parentTransaction;
        this.preparedSession = preparedSession;
        this.rollbackRequested = source.rollbackRequested;
        this.appliedOperation = source.appliedOperation;
        this.restoreConfigurations = source.restoreConfigurations;
        this.rollbackSession = source.rollbackSession;
        this.rollbackHealthVerified = source.rollbackHealthVerified;
        this.healthCheckedAt = source.healthCheckedAt;
        this.healthFailure = source.healthFailure;
    }

    /** Reads a journal written by {@link #save}. */
    @NotNull
    public static TransactionJournal load(@NotNull Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            TransactionJournal journal = GSON.fromJson(reader, TransactionJournal.class);
            if (journal == null) {
                throw new NullPointerException("Transaction journal is empty: " + path);
            }
            return journal;
        }
    }

    /** Writes the journal durably: after a power loss the journal is all that tells recovery which jars were half replaced. */
    public static void save(@NotNull Path path, @NotNull TransactionJournal journal) throws IOException {
        AtomicFiles.write(path, GSON.toJson(journal).getBytes(StandardCharsets.UTF_8), true);
    }

    /** Whether this is a saved component set (the only kind offered for rollback), not an internal installation. */
    public boolean isSavedSet() {
        return purpose == null || "STAGE".equals(purpose);
    }

    /**
     * Whether the set reached the disk and its backups can be restored: it committed, or the restarted server failed
     * its health check. A set that failed before publication changed nothing, and "restoring" it would put back files
     * that may be older than what is installed now, or delete a component installed later.
     */
    public boolean isRestorable() {
        return state == TransactionState.COMMITTED || (state == TransactionState.FAILED && healthFailure);
    }

    /** A copy with other artifact records. */
    @NotNull
    public TransactionJournal withArtifacts(@NotNull List<JournalArtifact> value) {
        return new TransactionJournal(this, new ArrayList<>(value), preparedSession);
    }

    /** A copy prepared by another process session. */
    @NotNull
    public TransactionJournal withPreparedSession(@Nullable String value) {
        return new TransactionJournal(this, artifacts, value);
    }

    /** A copy with the same content that can be changed independently. */
    @NotNull
    public TransactionJournal copy() {
        return new TransactionJournal(this, artifacts, preparedSession);
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public TransactionState getState() {
        return state;
    }

    public void setState(@NotNull TransactionState state) {
        this.state = state;
    }

    @NotNull
    public List<JournalArtifact> getArtifacts() {
        return artifacts;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public String getPlanId() {
        return planId;
    }

    @Nullable
    public Integer getTargetApi() {
        return targetApi;
    }

    @Nullable
    public String getConfigurationSnapshot() {
        return configurationSnapshot;
    }

    @Nullable
    public String getPurpose() {
        return purpose;
    }

    @Nullable
    public String getParentTransaction() {
        return parentTransaction;
    }

    @Nullable
    public String getPreparedSession() {
        return preparedSession;
    }

    public boolean getRollbackRequested() {
        return rollbackRequested;
    }

    public void setRollbackRequested(boolean rollbackRequested) {
        this.rollbackRequested = rollbackRequested;
    }

    @Nullable
    public String getAppliedOperation() {
        return appliedOperation;
    }

    public void setAppliedOperation(@Nullable String appliedOperation) {
        this.appliedOperation = appliedOperation;
    }

    public boolean getRestoreConfigurations() {
        return restoreConfigurations;
    }

    public void setRestoreConfigurations(boolean restoreConfigurations) {
        this.restoreConfigurations = restoreConfigurations;
    }

    @Nullable
    public String getRollbackSession() {
        return rollbackSession;
    }

    public void setRollbackSession(@Nullable String rollbackSession) {
        this.rollbackSession = rollbackSession;
    }

    public boolean getRollbackHealthVerified() {
        return rollbackHealthVerified;
    }

    public void setRollbackHealthVerified(boolean rollbackHealthVerified) {
        this.rollbackHealthVerified = rollbackHealthVerified;
    }

    @Nullable
    public String getHealthCheckedAt() {
        return healthCheckedAt;
    }

    public void setHealthCheckedAt(@Nullable String healthCheckedAt) {
        this.healthCheckedAt = healthCheckedAt;
    }

    public boolean getHealthFailure() {
        return healthFailure;
    }

    public void setHealthFailure(boolean healthFailure) {
        this.healthFailure = healthFailure;
    }
}
