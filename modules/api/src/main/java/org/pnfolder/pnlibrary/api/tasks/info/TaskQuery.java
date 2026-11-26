package org.pnfolder.pnlibrary.api.tasks.info;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;

/** Immutable filter used to find live and retained task snapshots. */
public final class TaskQuery {
    private final TaskId id;
    private final String key;
    private final String nameContains;
    private final Set<TaskStatus> statuses;
    private final Set<TaskExecution.Kind> executionKinds;
    private final Set<String> tags;

    private TaskQuery(
            TaskId id,
            String key,
            String nameContains,
            Set<TaskStatus> statuses,
            Set<TaskExecution.Kind> executionKinds,
            Set<String> tags) {
        this.id = id;
        this.key = key;
        this.nameContains = nameContains;
        this.statuses = statuses;
        this.executionKinds = executionKinds;
        this.tags = tags;
    }

    /** Starts a new query builder. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Returns a query matching every task. */
    @NotNull
    public static TaskQuery all() {
        return new Builder().build();
    }

    /** Exact task identifier to match, or {@code null}. */
    @Nullable
    public TaskId getId() {
        return id;
    }

    /** Exact application-defined task key to match, or {@code null}. */
    @Nullable
    public String getKey() {
        return key;
    }

    /** Case-insensitive name fragment to match, or {@code null}. */
    @Nullable
    public String getNameContains() {
        return nameContains;
    }

    /** Accepted lifecycle states; empty accepts every state. */
    @NotNull
    public Set<TaskStatus> getStatuses() {
        return statuses;
    }

    /** Accepted execution kinds; empty accepts every kind. */
    @NotNull
    public Set<TaskExecution.Kind> getExecutionKinds() {
        return executionKinds;
    }

    /** Tags that matching tasks must contain. */
    @NotNull
    public Set<String> getTags() {
        return tags;
    }

    /** Returns whether {@code snapshot} satisfies every populated criterion of this query. */
    public boolean matches(@NotNull TaskSnapshot snapshot) {
        return (id == null || snapshot.getId().equals(id))
                && (key == null || key.equals(snapshot.getKey()))
                && matchesName(snapshot.getName())
                && (statuses.isEmpty() || statuses.contains(snapshot.getStatus()))
                && (executionKinds.isEmpty() || executionKinds.contains(snapshot.getExecutionKind()))
                && snapshot.getTags().containsAll(tags);
    }

    private boolean matchesName(String name) {
        if (nameContains == null) {
            return true;
        }
        return name != null && name.toLowerCase(Locale.ROOT).contains(nameContains.toLowerCase(Locale.ROOT));
    }

    /** Fluent builder for a validated {@link TaskQuery}. */
    public static final class Builder {
        private TaskId id;
        private String key;
        private String nameContains;
        private final Set<TaskStatus> statuses = new LinkedHashSet<TaskStatus>();
        private final Set<TaskExecution.Kind> executionKinds = new LinkedHashSet<TaskExecution.Kind>();
        private final Set<String> tags = new LinkedHashSet<String>();

        private Builder() {}

        /** Matches exactly {@code value}. */
        @NotNull
        public Builder id(@NotNull TaskId value) {
            id = value;
            return this;
        }

        /** Matches the normalized application-defined task key. */
        @NotNull
        public Builder key(@NotNull String value) {
            key = value;
            return this;
        }

        /** Matches task names containing {@code value}. */
        @NotNull
        public Builder nameContains(@NotNull String value) {
            nameContains = value;
            return this;
        }

        /** Accepts only tasks in one of the given lifecycle states. */
        @NotNull
        public Builder status(@NotNull TaskStatus... value) {
            Collections.addAll(statuses, value);
            return this;
        }

        /** Accepts only tasks with one of the given execution kinds. */
        @NotNull
        public Builder execution(@NotNull TaskExecution.Kind... value) {
            Collections.addAll(executionKinds, value);
            return this;
        }

        /** Requires matching tasks to contain {@code value}. */
        @NotNull
        public Builder tag(@NotNull String value) {
            tags.add(value);
            return this;
        }

        /** Validates and creates the immutable query. */
        @NotNull
        public TaskQuery build() {
            String normalizedKey = key == null ? null : key.trim();
            String normalizedName = nameContains == null ? null : nameContains.trim();
            require(normalizedKey == null || !normalizedKey.isEmpty(), "Task query key must not be blank");
            require(normalizedName == null || !normalizedName.isEmpty(), "Task query name must not be blank");
            for (String tag : tags) {
                require(!tag.trim().isEmpty(), "Task query tags must not be blank");
            }
            return new TaskQuery(
                    id,
                    normalizedKey,
                    normalizedName,
                    Collections.unmodifiableSet(new LinkedHashSet<TaskStatus>(statuses)),
                    Collections.unmodifiableSet(new LinkedHashSet<TaskExecution.Kind>(executionKinds)),
                    Collections.unmodifiableSet(new LinkedHashSet<String>(tags)));
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalArgumentException(message);
            }
        }
    }
}
