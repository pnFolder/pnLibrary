package org.pnfolder.pnlibrary.api.tasks.info;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/** Stable non-blank identifier assigned to one scheduled task. */
public final class TaskId {
    private final String value;

    /**
     * Wraps a non-blank identifier.
     *
     * @throws IllegalArgumentException when {@code value} is blank
     */
    public TaskId(@NotNull String value) {
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("Task id must not be blank");
        }
        this.value = value;
    }

    /** Returns a new UUID-backed task identifier. */
    @NotNull
    public static TaskId random() {
        return new TaskId(UUID.randomUUID().toString());
    }

    /** Non-blank identifier value. */
    @NotNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof TaskId && value.equals(((TaskId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
