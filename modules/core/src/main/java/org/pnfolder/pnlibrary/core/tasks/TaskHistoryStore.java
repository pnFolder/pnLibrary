package org.pnfolder.pnlibrary.core.tasks;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.common.io.TsvRecords;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Older format (16 fields) still loads; newer fields default. Unreadable lines are skipped silently.
/** The finished tasks, kept in a file so the history survives a restart. */
public final class TaskHistoryStore {
    private static final String HEADER = "#pnlibrary-task-history 3";
    private static final int FIRST_FIELDS = 16;
    private static final int SECOND_FIELDS = 21;
    private static final int FIELDS = 22;
    private static final String TAG_SEPARATOR = "\u001F";

    private final Path file;

    /** A store in {@code file}. */
    public TaskHistoryStore(@NotNull Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /** The tasks in the file; lines that cannot be read are left out. */
    @NotNull
    public List<TaskSnapshot> load() {
        List<TaskSnapshot> loaded = new ArrayList<>();
        for (List<String> fields : TsvRecords.read(file)) {
            try {
                loaded.add(parse(fields));
            } catch (RuntimeException unreadable) {
                // A damaged line costs one history entry, not the whole file.
            }
        }
        return loaded;
    }

    /** Replaces the file with {@code snapshots}. */
    public void save(@NotNull List<TaskSnapshot> snapshots) {
        List<List<Object>> records = new ArrayList<>();
        for (TaskSnapshot snapshot : snapshots) {
            records.add(format(snapshot));
        }
        TsvRecords.write(file, HEADER, records);
    }

    private static List<Object> format(TaskSnapshot task) {
        return Arrays.asList(
                task.getId().getValue(), task.getName(), task.getKey(), task.getOwnerName(), task.getExecutionKind().name(),
                task.getStatus().name(), join(task.getTags()), task.getCreatedAt(), task.getNextRunAt(), task.getRunCount(),
                task.getSkippedCount(), task.getLastStartedAt(), task.getLastCompletedAt(), task.getLastFailure(),
                task.getDescription(), task.getUsage(), task.getSource(),
                task.getInterval() == null ? null : task.getInterval().toMillis(),
                task.getLastDuration() == null ? null : task.getLastDuration().toNanos(),
                task.getCancellable(), task.getRunnableNow(), task.getHidden());
    }

    private static String join(Set<String> tags) {
        StringBuilder joined = new StringBuilder();
        for (String tag : tags) {
            joined.append(joined.length() == 0 ? "" : TAG_SEPARATOR).append(tag);
        }
        return joined.toString();
    }

    private static TaskSnapshot parse(List<String> f) {
        if (f.size() != FIELDS && f.size() != SECOND_FIELDS && f.size() != FIRST_FIELDS) {
            throw new IllegalArgumentException("Task history line has " + f.size() + " fields");
        }
        boolean newer = f.size() >= SECOND_FIELDS;
        return TaskSnapshot.builder(new TaskId(f.get(0)), f.get(3), TaskExecution.Kind.valueOf(f.get(4)),
                        TaskStatus.valueOf(f.get(5)), Instant.parse(f.get(7)))
                .name(text(f, 1))
                .key(text(f, 2))
                .tags(tags(f.get(6)))
                .nextRunAt(time(f, 8))
                .runCount(Long.parseLong(f.get(9)))
                .skippedCount(Long.parseLong(f.get(10)))
                .lastStartedAt(time(f, 11))
                .lastCompletedAt(time(f, 12))
                .lastFailure(text(f, 13))
                .description(text(f, 14))
                .usage(text(f, 15))
                .source(newer ? text(f, 16) : null)
                .interval(newer && text(f, 17) != null ? Duration.ofMillis(Long.parseLong(text(f, 17))) : null)
                .lastDuration(newer && text(f, 18) != null ? Duration.ofNanos(Long.parseLong(text(f, 18))) : null)
                .cancellable(!newer || Boolean.parseBoolean(f.get(19)))
                .runnableNow(!newer || Boolean.parseBoolean(f.get(20)))
                .hidden(f.size() == FIELDS && Boolean.parseBoolean(f.get(21)))
                .build();
    }

    private static Set<String> tags(String joined) {
        Set<String> tags = new LinkedHashSet<>();
        for (String tag : joined.split(TAG_SEPARATOR, -1)) {
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private static String text(List<String> fields, int index) {
        String value = fields.get(index);
        return value.isEmpty() ? null : value;
    }

    private static Instant time(List<String> fields, int index) {
        String value = text(fields, index);
        return value == null ? null : Instant.parse(value);
    }
}
