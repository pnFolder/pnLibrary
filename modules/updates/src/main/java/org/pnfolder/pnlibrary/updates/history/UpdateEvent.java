package org.pnfolder.pnlibrary.updates.history;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor;
import org.pnfolder.pnlibrary.api.updates.state.UpdateState;

/** One journal entry: what was observed for a plan, who caused it and which products changed. */
public final class UpdateEvent {
    private final UUID id;
    private final Instant occurredAt;
    private final UUID planId;
    private final long revision;
    private final String state;
    private final List<UpdateEventChange> changes;
    private final UpdateActor actor;
    private final String operation;
    private final String message;

    /** Creates an entry. */
    public UpdateEvent(@NotNull UUID id, @NotNull Instant occurredAt, @NotNull UUID planId, long revision,
                       @NotNull String state, @NotNull List<UpdateEventChange> changes, @NotNull UpdateActor actor,
                       @NotNull String operation, @Nullable String message) {
        this.id = Objects.requireNonNull(id, "id");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.planId = Objects.requireNonNull(planId, "planId");
        this.revision = revision;
        this.state = Objects.requireNonNull(state, "state");
        this.changes = Objects.requireNonNull(changes, "changes");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.message = message;
    }

    /** The id of the entry. */
    @NotNull
    public UUID getId() {
        return id;
    }

    /** When the state was observed. */
    @NotNull
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /** The plan the entry belongs to. */
    @NotNull
    public UUID getPlanId() {
        return planId;
    }

    /** The revision of the plan snapshot. */
    public long getRevision() {
        return revision;
    }

    /** The name of the observed {@link UpdateState}. */
    @NotNull
    public String getState() {
        return state;
    }

    /** The products that changed. */
    @NotNull
    public List<UpdateEventChange> getChanges() {
        return changes;
    }

    /** Who caused the operation. */
    @NotNull
    public UpdateActor getActor() {
        return actor;
    }

    /** What happened, such as {@code CHECK} or {@code STAGE}. */
    @NotNull
    public String getOperation() {
        return operation;
    }

    /** A note about the entry, or null. */
    @Nullable
    public String getMessage() {
        return message;
    }

    /** A scheduled check that found nothing: kept for auditing, but left out of the history people read. */
    public boolean isRoutine() {
        return operation.equals("CHECK") && changes.isEmpty() && message == null
            && state.equals(UpdateState.UP_TO_DATE.name());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UpdateEvent)) {
            return false;
        }
        UpdateEvent that = (UpdateEvent) other;
        return revision == that.revision && id.equals(that.id) && occurredAt.equals(that.occurredAt)
            && planId.equals(that.planId) && state.equals(that.state) && changes.equals(that.changes)
            && actor.equals(that.actor) && operation.equals(that.operation) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, occurredAt, planId, revision, state, changes, actor, operation, message);
    }

    @Override
    public String toString() {
        return "Event(id=" + id + ", occurredAt=" + occurredAt + ", planId=" + planId + ", revision=" + revision
            + ", state=" + state + ", changes=" + changes + ", actor=" + actor + ", operation=" + operation
            + ", message=" + message + ")";
    }
}
