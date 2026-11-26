package org.pnfolder.pnlibrary.core.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

class ScheduledTaskAdminTest {
    @Test
    void anAdministratorCannotStopATaskThatForbidsItButItsOwnerCan() {
        ScheduledTasks service = new ScheduledTasks(new RecordingTaskAdapter());
        TaskHandle task = service.scope(new Object())
                .schedule(TaskSpec.builder().name("locked").cancellable(false).action(context -> { }).build());

        assertEquals(TaskControl.FORBIDDEN, service.cancelByAdmin(task.getId()));
        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
        assertFalse(service.query().get(0).getCancellable());

        task.cancel();
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertEquals(TaskControl.UNAVAILABLE, service.cancelByAdmin(task.getId()));
    }

    @Test
    void runNowExecutesAWaitingRepeatingTaskOnceAndKeepsItsSchedule() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter();
        ScheduledTasks service = new ScheduledTasks(adapter);
        AtomicInteger runs = new AtomicInteger();
        TaskHandle task = service.scope(new Object()).schedule(TaskSpec.builder().name("report")
                .interval(Duration.ofMinutes(5)).delay(Duration.ofMinutes(1)).action(context -> runs.incrementAndGet()).build());
        java.time.Instant planned = service.query().get(0).getNextRunAt();

        assertEquals(TaskControl.DONE, service.runNow(task.getId()));
        adapter.fire(1);

        assertEquals(1, runs.get());
        TaskSnapshot snapshot = service.query().get(0);
        assertEquals(TaskStatus.SCHEDULED, snapshot.getStatus());
        assertEquals(planned, snapshot.getNextRunAt());
        assertEquals(1L, snapshot.getRunCount());
        assertNotNull(snapshot.getLastDuration());
        assertEquals(Duration.ofMinutes(5), snapshot.getInterval());
    }

    @Test
    void aTaskIsVisibleUnlessItAsksToBeHidden() {
        ScheduledTasks service = new ScheduledTasks(new RecordingTaskAdapter());
        TaskScope scope = service.scope(new Object());
        scope.schedule(TaskSpec.builder().name("shown").action(context -> { }).build());
        scope.schedule(TaskSpec.builder().name("secret").hidden(true).action(context -> { }).build());

        Map<String, Boolean> hidden = new HashMap<>();
        for (TaskSnapshot snapshot : service.query()) {
            hidden.put(snapshot.getName(), snapshot.getHidden());
        }
        assertEquals(2, hidden.size());
        assertFalse(hidden.get("shown"));
        assertTrue(hidden.get("secret"));
    }

    @Test
    void runNowIsRefusedWhenTheTaskForbidsIt() {
        RecordingTaskAdapter adapter = new RecordingTaskAdapter();
        ScheduledTasks service = new ScheduledTasks(adapter);
        TaskHandle task = service.scope(new Object())
                .schedule(TaskSpec.builder().name("fixed").runnableNow(false).action(context -> { }).build());

        assertEquals(TaskControl.FORBIDDEN, service.runNow(task.getId()));
        assertEquals(1, adapter.requests.size());
    }
}
