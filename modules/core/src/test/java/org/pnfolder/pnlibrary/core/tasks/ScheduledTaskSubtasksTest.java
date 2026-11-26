package org.pnfolder.pnlibrary.core.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.TaskScope;
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.run.LagResponse;
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext;
import org.pnfolder.pnlibrary.api.tasks.spec.ScheduledTask;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskConflictPolicy;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpecBuilder;

class ScheduledTaskSubtasksTest {
    private final RecordingTaskAdapter adapter = new RecordingTaskAdapter();
    private final TaskScope scope = new ScheduledTasks(adapter).scope(new Object());

    private static final class RewardTask extends ScheduledTask {
        private final List<String> log;

        RewardTask(List<String> log) {
            this.log = log;
        }

        @Override
        public String getName() {
            return "Награда";
        }

        @Override
        public Duration getDelay() {
            return Duration.ofMinutes(1);
        }

        @Override
        public boolean getCancelWithParent() {
            return true;
        }

        @Override
        public void run(TaskContext context) {
            log.add("reward");
        }
    }

    /** The examples of {@code docs/tasks.md}: if they stop compiling or working, the documentation is wrong. */
    @Test
    void aTaskBuiltWithTheDslStartsSubtasksOfEveryKind() {
        List<String> log = new ArrayList<String>();
        TaskSpecBuilder.schedule(scope, b -> {
            b.setName("Ивент");
            b.setKey("event");
            b.setConflictPolicy(TaskConflictPolicy.REPLACE);
            b.tag("event");
            b.setExecution(TaskExecution.global());
            b.setInterval(Duration.ofSeconds(1));
            b.setMaxLag(Duration.ofSeconds(10));
            b.setTolerateFailures(3);
            b.condition(() -> true);
            b.cancelWhen(() -> false);
            b.whenLate(Duration.ofSeconds(1), lag -> log.add("late"));
            b.onLag(Duration.ofSeconds(2), lag -> LagResponse.RUN);
            b.run(ctx -> {
                if (ctx.isFirstRun()) {
                    spawnChildren(ctx, log);
                }
            });
        });

        adapter.fire(0);

        List<TaskSnapshot> all = scope.query(TaskQuery.all());
        TaskSnapshot parent = null;
        for (TaskSnapshot snapshot : all) {
            if ("Ивент".equals(snapshot.getName())) {
                parent = snapshot;
            }
        }
        java.util.Set<String> children = new HashSet<String>();
        for (TaskSnapshot snapshot : all) {
            if (snapshot.getParentId() != null && snapshot.getParentId().equals(parent.getId())) {
                children.add(snapshot.getName());
            }
        }
        assertEquals(new HashSet<String>(Arrays.asList("Таймер", "Награда", "Ивент / later", "Ивент / repeat")), children);
    }

    private static void spawnChildren(TaskContext ctx, List<String> log) {
        TaskSpecBuilder.spawn(ctx, child -> {
            child.setName("Таймер");
            child.setInterval(Duration.ofSeconds(1));
            child.setCancelWithParent(true);
            child.run(c -> log.add("tick"));
        });
        ctx.spawn(new RewardTask(log));
        ctx.later(Duration.ofSeconds(5), c -> log.add("later"));
        ctx.repeat(2, Duration.ofSeconds(1), c -> log.add("repeat"));
    }

    @Test
    void aReadySpecCanBeStartedAsASubtaskManyTimes() {
        TaskSpec timer = TaskSpec.builder().name("Таймер").cancelWithParent(true).action(c -> { }).build();
        scope.schedule(TaskSpec.builder().name("Ивент").whenLate(Duration.ofSeconds(1), lag -> { })
                .onLag(Duration.ofSeconds(2), lag -> LagResponse.SKIP)
                .action(ctx -> {
                    ctx.spawn(timer);
                    ctx.spawn(timer);
                }).build());

        adapter.fire(0);

        assertEquals(3, adapter.requests.size());
        int children = 0;
        for (TaskSnapshot snapshot : scope.query(TaskQuery.all())) {
            children += snapshot.getParentId() != null ? 1 : 0;
        }
        assertEquals(2, children);
    }
}
