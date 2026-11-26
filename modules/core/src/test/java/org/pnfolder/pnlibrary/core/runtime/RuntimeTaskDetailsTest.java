package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeTaskDetails;

class RuntimeTaskDetailsTest {
    @Test
    void taskDetailsPreserveStableIdentityAndAggregateFailures() {
        TaskSnapshot snapshot = TaskSnapshot.builder(new TaskId("task-1"), "DemoPlugin", TaskExecution.Kind.GLOBAL,
                TaskStatus.FAILED, Instant.EPOCH)
            .name("refresh")
            .tags(Collections.emptySet())
            .runCount(2)
            .skippedCount(1)
            .lastStartedAt(Instant.EPOCH)
            .lastFailure("boom")
            .build();

        Map<String, Object> entry = RuntimeTaskDetails.entries(Collections.singletonList(snapshot)).get(0);
        Map<String, Object> summary = RuntimeTaskDetails.summary(Collections.singletonList(snapshot));

        assertEquals("task-1", entry.get("id"));
        assertEquals("GLOBAL", entry.get("execution"));
        assertEquals(1, summary.get("failedCount"));
        assertEquals(Collections.singletonMap("DemoPlugin", 1), summary.get("failuresByOwner"));
    }
}
