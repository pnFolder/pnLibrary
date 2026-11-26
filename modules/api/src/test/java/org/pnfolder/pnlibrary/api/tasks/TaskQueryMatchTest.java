package org.pnfolder.pnlibrary.api.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;

class TaskQueryMatchTest {
    private final TaskSnapshot snapshot = TaskSnapshot.builder(
                    new TaskId("task-1"), "ExamplePlugin", TaskExecution.Kind.ASYNC, TaskStatus.RUNNING, Instant.EPOCH)
            .name("Refresh player cache")
            .key("cache-refresh")
            .tags(new LinkedHashSet<>(Arrays.asList("cache", "players")))
            .runCount(2)
            .lastStartedAt(Instant.EPOCH)
            .build();

    @Test
    void matchesEveryPopulatedQueryCriterion() {
        TaskQuery query = TaskQuery.builder()
                .id(snapshot.getId())
                .key("cache-refresh")
                .nameContains("PLAYER")
                .status(TaskStatus.RUNNING)
                .execution(TaskExecution.Kind.ASYNC)
                .tag("cache")
                .build();

        assertTrue(query.matches(snapshot));
    }

    @Test
    void rejectsSnapshotWhenAnyCriterionDiffers() {
        assertFalse(TaskQuery.builder().key("other").build().matches(snapshot));
        assertFalse(TaskQuery.builder().status(TaskStatus.FAILED).build().matches(snapshot));
        assertFalse(TaskQuery.builder().tag("database").build().matches(snapshot));
    }

    @Test
    void terminalStatusesAndTheActiveFlagAgree() {
        Set<TaskStatus> terminal = EnumSet.noneOf(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            if (status.isTerminal()) {
                terminal.add(status);
            }
        }
        assertEquals(EnumSet.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.FAILED), terminal);
        assertTrue(snapshot.isActive());
        assertFalse(snapshot.toBuilder().status(TaskStatus.FAILED).build().isActive());
    }
}
