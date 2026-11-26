package org.pnfolder.pnlibrary.api.updates.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;
import org.pnfolder.pnlibrary.api.updates.release.ProductChange;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

/** One durable update event, detached from the current resolver plan. */
public final class UpdateHistoryEntry {
    private final UUID id;
    private final Instant occurredAt;
    private final UUID planId;
    private final UpdateState state;
    private final UpdateActor actor;
    private final String operation;
    private final List<ProductChange> changes;
    private final String message;

    /**
     * Creates the entry.
     *
     * @param id unique event identity
     * @param occurredAt UTC event time, not release publication time
     * @param planId related compatibility plan
     * @param state observed outcome; UPDATE_STAGED means prepared, not running
     * @param actor initiator, or UNKNOWN for legacy observations
     * @param operation CHECK, STAGE, ROLLBACK, HEALTH, HEALTH_ROLLBACK or STATE_OBSERVED
     * @param changes version transitions associated with this event
     * @param message optional diagnostic detail, never intended as formatted chat markup
     */
    public UpdateHistoryEntry(@NotNull UUID id, @NotNull Instant occurredAt, @NotNull UUID planId,
                              @NotNull UpdateState state, @NotNull UpdateActor actor, @NotNull String operation,
                              @NotNull List<ProductChange> changes, @Nullable String message) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.planId = planId;
        this.state = state;
        this.actor = actor;
        this.operation = operation;
        this.changes = UpdateValidation.frozen(changes);
        this.message = message;
    }

    /** Unique event identity. */
    @NotNull
    public UUID getId() {
        return id;
    }

    /** UTC event time, not release publication time. */
    @NotNull
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /** Related compatibility plan. */
    @NotNull
    public UUID getPlanId() {
        return planId;
    }

    /** Observed outcome; UPDATE_STAGED means prepared, not running. */
    @NotNull
    public UpdateState getState() {
        return state;
    }

    /** Initiator, or UNKNOWN for legacy observations. */
    @NotNull
    public UpdateActor getActor() {
        return actor;
    }

    /** CHECK, STAGE, ROLLBACK, HEALTH, HEALTH_ROLLBACK or STATE_OBSERVED. */
    @NotNull
    public String getOperation() {
        return operation;
    }

    /** Immutable version transitions associated with this event. */
    @NotNull
    public List<ProductChange> getChanges() {
        return changes;
    }

    /** Optional diagnostic detail, never intended as formatted chat markup. */
    @Nullable
    public String getMessage() {
        return message;
    }
}
