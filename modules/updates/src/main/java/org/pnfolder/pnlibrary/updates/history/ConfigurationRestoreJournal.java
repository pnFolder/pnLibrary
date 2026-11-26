package org.pnfolder.pnlibrary.updates.history;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The progress of one restore, saved after each step; {@code state} says how far it got. */
public final class ConfigurationRestoreJournal {
    private final String id;
    private final String source;
    private final String before;
    private String state;
    private final String operation;

    /** Creates a journal of a restore that is not tied to an operation. */
    public ConfigurationRestoreJournal(@NotNull String id, @NotNull String source, @NotNull String before,
                                       @NotNull String state) {
        this(id, source, before, state, null);
    }

    /** Creates a journal of the restore that belongs to {@code operation}. */
    public ConfigurationRestoreJournal(@NotNull String id, @NotNull String source, @NotNull String before,
                                       @NotNull String state, @Nullable String operation) {
        this.id = id;
        this.source = source;
        this.before = before;
        this.state = state;
        this.operation = operation;
    }

    /** The restore id. */
    @NotNull
    public String getId() {
        return id;
    }

    /** The snapshot being restored. */
    @NotNull
    public String getSource() {
        return source;
    }

    /** The snapshot of the files as they were before the restore. */
    @NotNull
    public String getBefore() {
        return before;
    }

    /** How far the restore got. */
    @NotNull
    public String getState() {
        return state;
    }

    /** Sets how far the restore got. */
    public void setState(@NotNull String state) {
        this.state = state;
    }

    /** The operation the restore belongs to, or null. */
    @Nullable
    public String getOperation() {
        return operation;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConfigurationRestoreJournal)) {
            return false;
        }
        ConfigurationRestoreJournal that = (ConfigurationRestoreJournal) other;
        return id.equals(that.id) && source.equals(that.source) && before.equals(that.before)
            && state.equals(that.state) && Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source, before, state, operation);
    }

    @Override
    public String toString() {
        return "ConfigurationRestoreJournal(id=" + id + ", source=" + source + ", before=" + before + ", state=" + state
            + ", operation=" + operation + ")";
    }
}
