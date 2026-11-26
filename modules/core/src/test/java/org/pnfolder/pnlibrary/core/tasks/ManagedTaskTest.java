package org.pnfolder.pnlibrary.core.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskConflictPolicy;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

class ManagedTaskTest {
    private final RecordingTaskAdapter adapter = new RecordingTaskAdapter();
    private final List<ManagedTask> terminal = new ArrayList<ManagedTask>();

    private ManagedTask task(TaskSpec spec) {
        return new ManagedTask(new Object(), new Object(), spec, adapter, (owner, message, error) -> { }, terminal::add);
    }

    @Test
    void cancelWinsOnceAndLaterRunsDoNothing() {
        AtomicInteger runs = new AtomicInteger();
        ManagedTask task = task(TaskSpec.builder().action(context -> runs.incrementAndGet()).build());
        assertTrue(task.cancelIfActive());
        assertFalse(task.cancelIfActive());
        task.invoke();
        assertEquals(0, runs.get());
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertTrue(task.isCancelled());
        assertEquals(1, terminal.size());
    }

    @Test
    void cancelDuringTheRunIsNotOverwrittenWhenTheRunEnds() {
        AtomicReference<ManagedTask> holder = new AtomicReference<ManagedTask>();
        ManagedTask task = task(TaskSpec.builder().interval(Duration.ofSeconds(1)).action(context -> holder.get().cancel()).build());
        holder.set(task);
        task.invoke();
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertEquals(1, terminal.size());
    }

    @Test
    void oneShotTaskCompletesOnceAndRepeatingTaskReturnsToScheduled() {
        ManagedTask once = task(TaskSpec.builder().action(context -> { }).build());
        once.invoke();
        once.invoke();
        assertEquals(TaskStatus.COMPLETED, once.getStatus());
        assertEquals(1, once.snapshot().getRunCount());
        ManagedTask repeat = task(TaskSpec.builder().interval(Duration.ofSeconds(1)).action(context -> { }).build());
        repeat.invoke();
        repeat.invoke();
        assertEquals(TaskStatus.SCHEDULED, repeat.getStatus());
        assertEquals(2, repeat.snapshot().getRunCount());
        assertNotNull(repeat.snapshot().getNextRunAt());
    }

    @Test
    void failingActionMarksTheTaskFailedOnceAndReportsIt() {
        ManagedTask task = task(TaskSpec.builder().action(context -> {
            throw new IllegalStateException("boom");
        }).build());
        task.invoke();
        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertTrue(task.snapshot().getLastFailure().contains("boom"));
        assertEquals(1, terminal.size());
    }

    @Test
    void cancelWhenConditionCancelsTheTaskBeforeItRuns() {
        AtomicInteger runs = new AtomicInteger();
        AtomicBoolean flag = new AtomicBoolean();
        ManagedTask task = task(TaskSpec.builder().interval(Duration.ofSeconds(1)).cancelWhen(flag::get)
                .action(context -> runs.incrementAndGet()).build());
        task.invoke();
        assertEquals(1, runs.get());
        flag.set(true);
        task.invoke();
        assertEquals(1, runs.get());
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    void conflictByKeyKeepsExistingOrReplacesIt() {
        ScheduledTasks service = new ScheduledTasks(adapter);
        TaskScope scope = service.scope(new Object());
        TaskHandle first = scope.schedule(TaskSpec.builder().key("k").action(context -> { }).build());
        TaskHandle kept = scope.schedule(TaskSpec.builder().key("k")
                .conflictPolicy(TaskConflictPolicy.KEEP_EXISTING).action(context -> { }).build());
        assertSame(first, kept);
        TaskHandle replaced = scope.schedule(TaskSpec.builder().key("k")
                .conflictPolicy(TaskConflictPolicy.REPLACE).action(context -> { }).build());
        assertEquals(TaskStatus.CANCELLED, first.getStatus());
        assertEquals(TaskStatus.SCHEDULED, replaced.getStatus());
        service.close();
    }

    @Test
    void globalTaskWithZeroDelayIsRequestedWithZeroDelayAndRuns() {
        ScheduledTasks service = new ScheduledTasks(adapter);
        AtomicBoolean ran = new AtomicBoolean();
        TaskHandle handle = service.scope(new Object()).schedule(
                TaskSpec.builder().execution(TaskExecution.global()).action(context -> ran.set(true)).build());
        assertEquals(1, adapter.requests.size());
        assertEquals(TaskExecution.Kind.GLOBAL, adapter.requests.get(0).getExecutionKind());
        assertEquals(Duration.ZERO, adapter.requests.get(0).getDelay());
        adapter.requests.get(0).getCallback().run();
        assertTrue(ran.get());
        assertEquals(TaskStatus.COMPLETED, handle.getStatus());
        service.close();
    }

    @Test
    void runNowStartsAWaitingTaskWithZeroDelayAndRefusesAFinishedOne() {
        AtomicInteger runs = new AtomicInteger();
        ManagedTask task = task(TaskSpec.builder().delay(Duration.ofMinutes(5)).action(context -> runs.incrementAndGet()).build());
        assertTrue(task.runNow());
        assertEquals(1, adapter.requests.size());
        assertEquals(Duration.ZERO, adapter.requests.get(0).getDelay());
        adapter.requests.get(0).getCallback().run();
        assertEquals(1, runs.get());
        assertEquals(TaskStatus.COMPLETED, task.getStatus());
        assertFalse(task.runNow());
    }
}
