package org.pnfolder.pnlibrary.core.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.TaskHandle;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.info.TaskLag;
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus;
import org.pnfolder.pnlibrary.api.tasks.run.LagResponse;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

class ScheduledTaskRunsTest {
    private final RecordingTaskAdapter adapter = new RecordingTaskAdapter();
    private final TaskScope scope = new ScheduledTasks(adapter).scope(new Object());

    private static void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    @Test
    void theContextReportsTheRunThePlanTheSettingsAndCancelsAfterALimit() {
        List<String> seen = new ArrayList<>();
        TaskHandle handle = scope.schedule(TaskSpec.builder().name("ctx").key("k").tag("t").interval(Duration.ofSeconds(1))
                .action(c -> {
                    seen.add(c.getRunNumber() + ":" + c.isFirstRun() + ":" + c.every(2) + ":" + c.getKey() + ":" + c.getTags()
                            + ":" + c.isRepeating() + ":" + (c.getPreviousStartedAt() != null));
                    if (c.getLag().isNegative() || c.snapshot().getRunCount() != c.getRunNumber()) {
                        throw new IllegalStateException("inconsistent context");
                    }
                    c.cancelAfter(3);
                }).build());

        for (int run = 0; run < 3; run++) {
            adapter.fire(0);
        }

        assertEquals(Arrays.asList("1:true:false:k:[t]:true:false", "2:false:true:k:[t]:true:true", "3:false:false:k:[t]:true:true"), seen);
        assertEquals(TaskStatus.CANCELLED, handle.getStatus());
    }

    @Test
    void aTaskStartsAFollowUpThatStaysInTheSameScope() {
        List<String> ran = new ArrayList<>();
        scope.schedule(TaskSpec.builder().name("parent")
                .action(c -> c.later(Duration.ofSeconds(5), later -> ran.add("later"))).build());

        adapter.fire(0);

        assertEquals(2, adapter.requests.size());
        assertEquals(Duration.ofSeconds(5), adapter.requests.get(1).getDelay());
        TaskSnapshot child = scope.query(TaskQuery.builder().tag("subtask").build()).get(0);
        assertEquals("parent / later", child.getName());
        adapter.fire(1);
        assertEquals(Arrays.asList("later"), ran);
    }

    @Test
    void eachRunOfARepeatingTaskIsPlannedOneIntervalAfterThePreviousStart() {
        List<Instant> planned = new ArrayList<>();
        scope.schedule(TaskSpec.builder().interval(Duration.ofSeconds(10)).action(c -> planned.add(c.getScheduledAt())).build());

        adapter.fire(0);
        adapter.fire(0);

        assertTrue(planned.get(1).isAfter(planned.get(0).plusSeconds(9)));
    }

    @Test
    void aRunThatStartsLaterThanMaxLagIsSkipped() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();
        TaskHandle handle = scope.schedule(TaskSpec.builder().maxLag(Duration.ofMillis(1)).interval(Duration.ofSeconds(1))
                .action(c -> ran.set(true)).build());

        sleep(30);
        adapter.fire(0);

        assertFalse(ran.get());
        assertEquals(1, handle.snapshot().getSkippedCount());
        assertEquals(TaskStatus.SCHEDULED, handle.getStatus());
    }

    @Test
    void aRepeatingTaskSurvivesToleratedFailuresInARowAndThenFails() {
        TaskHandle handle = scope.schedule(TaskSpec.builder().interval(Duration.ofSeconds(1)).tolerateFailures(2)
                .action(c -> {
                    throw new IllegalStateException("boom");
                }).build());

        adapter.fire(0);
        adapter.fire(0);
        assertEquals(TaskStatus.SCHEDULED, handle.getStatus());
        adapter.fire(0);

        assertEquals(TaskStatus.FAILED, handle.getStatus());
        assertEquals(3, handle.snapshot().getFailedCount());
    }

    @Test
    void aSuccessResetsTheFailureCount() {
        AtomicBoolean fail = new AtomicBoolean(true);
        TaskHandle handle = scope.schedule(TaskSpec.builder().interval(Duration.ofSeconds(1)).tolerateFailures(1)
                .action(c -> {
                    if (fail.get()) {
                        throw new IllegalStateException("boom");
                    }
                }).build());

        adapter.fire(0);
        fail.set(false);
        adapter.fire(0);
        fail.set(true);
        adapter.fire(0);

        assertEquals(TaskStatus.SCHEDULED, handle.getStatus());
    }

    @Test
    void aLagHandlerHearsAboutALateRunAndCanSkipIt() throws Exception {
        List<TaskLag> heard = new ArrayList<>();
        AtomicBoolean ran = new AtomicBoolean();
        TaskHandle handle = scope.schedule(TaskSpec.builder().interval(Duration.ofMillis(10))
                .onLag(Duration.ofMillis(5), lag -> {
                    heard.add(lag);
                    return LagResponse.SKIP;
                }).action(c -> ran.set(true)).build());

        sleep(50);
        adapter.fire(0);

        assertFalse(ran.get());
        assertEquals(1, heard.size());
        assertTrue(heard.get(0).getMissedRuns() >= 3);
        assertTrue(handle.snapshot().getWorstLag().compareTo(Duration.ofMillis(40)) >= 0);
        assertEquals(1, handle.snapshot().getSkippedCount());
    }

    @Test
    void aLagHandlerCanCancelTheTaskForGood() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();
        TaskHandle handle = scope.schedule(TaskSpec.builder().interval(Duration.ofMillis(10))
                .onLag(Duration.ofMillis(5), lag -> LagResponse.CANCEL).action(c -> ran.set(true)).build());

        sleep(50);
        adapter.fire(0);

        assertFalse(ran.get());
        assertEquals(TaskStatus.CANCELLED, handle.getStatus());
        assertNull(handle.snapshot().getNextRunAt());
    }

    @Test
    void aLagHandlerThatThrowsDoesNotStopTheRun() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();
        scope.schedule(TaskSpec.builder().interval(Duration.ofMillis(10))
                .onLag(Duration.ofMillis(1), lag -> {
                    throw new IllegalStateException("handler broke");
                }).action(c -> ran.set(true)).build());

        sleep(20);
        adapter.fire(0);

        assertTrue(ran.get());
    }

    @Test
    void aSubtaskKnowsItsParentAndALinkedOneIsCancelledWithIt() {
        TaskHandle parent = scope.schedule(TaskSpec.builder().name("parent").interval(Duration.ofSeconds(1)).action(c -> {
            if (c.getRunNumber() == 1L) {
                c.repeat(3, Duration.ofSeconds(1), child -> { });
                c.later(Duration.ofSeconds(1), child -> { });
            }
        }).build());

        adapter.fire(0);

        List<TaskSnapshot> children = scope.query(TaskQuery.builder().tag("subtask").build());
        assertEquals(2, children.size());
        for (TaskSnapshot child : children) {
            assertEquals(parent.getId(), child.getParentId());
        }
        parent.cancel();
        Map<String, TaskStatus> states = new HashMap<>();
        for (TaskSnapshot child : scope.query(TaskQuery.builder().tag("subtask").build())) {
            states.put(child.getName(), child.getStatus());
        }
        assertEquals(TaskStatus.CANCELLED, states.get("parent / repeat"));
        assertEquals(TaskStatus.SCHEDULED, states.get("parent / later"));
    }

    @Test
    void falseConditionsSkipRepeatButCompleteOneShot() {
        TaskHandle repeating = scope.schedule(TaskSpec.builder().interval(Duration.ofSeconds(1))
                .condition(() -> false).action(c -> {
                    throw new AssertionError("must not run");
                }).build());
        TaskHandle once = scope.schedule(TaskSpec.builder().condition(() -> false).action(c -> {
            throw new AssertionError("must not run");
        }).build());
        assertTrue(repeating.snapshot().getNextRunAt() != null);
        adapter.fire(0);
        adapter.fire(1);
        assertEquals(TaskStatus.SCHEDULED, repeating.getStatus());
        assertEquals(1, repeating.snapshot().getSkippedCount());
        assertEquals(TaskStatus.COMPLETED, once.getStatus());
    }

    @Test
    void aRepeatingTaskThatFailedDoesNotRunAgain() {
        AtomicReference<Integer> runs = new AtomicReference<>(0);
        TaskHandle task = scope.schedule(TaskSpec.builder().interval(Duration.ofSeconds(1)).action(c -> {
            runs.set(runs.get() + 1);
            throw new IllegalStateException("broken");
        }).build());
        adapter.fire(0);
        adapter.fire(0);

        assertEquals(1, runs.get().intValue());
        assertEquals(TaskStatus.FAILED, task.getStatus());
    }

}
