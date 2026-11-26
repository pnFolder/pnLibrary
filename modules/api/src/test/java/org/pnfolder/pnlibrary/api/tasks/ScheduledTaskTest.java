package org.pnfolder.pnlibrary.api.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext;
import org.pnfolder.pnlibrary.api.tasks.spec.ScheduledTask;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskConflictPolicy;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

class ScheduledTaskTest {
    private static final class Autosave extends ScheduledTask {
        private final String world;
        boolean active = true;

        Autosave(String world) {
            this.world = world;
        }

        @Override
        public String getName() {
            return "Autosave " + world;
        }

        @Override
        public String getKey() {
            return "autosave-" + world;
        }

        @Override
        public Duration getInterval() {
            return Duration.ofMinutes(5);
        }

        @Override
        public TaskExecution getExecution() {
            return TaskExecution.async();
        }

        @Override
        public Set<String> getTags() {
            return Collections.singleton("save");
        }

        @Override
        public boolean shouldRun() {
            return active;
        }

        @Override
        public void run(TaskContext context) {}
    }

    @Test
    void aTaskClassBecomesASpecWithItsOwnArguments() {
        TaskSpec spec = new Autosave("nether").toSpec();

        assertEquals("Autosave nether", spec.getName());
        assertEquals("autosave-nether", spec.getKey());
        assertEquals(Duration.ofMinutes(5), spec.getInterval());
        assertEquals(TaskExecution.Kind.ASYNC, spec.getExecution().getKind());
        assertEquals(Collections.singleton("save"), spec.getTags());
        assertEquals(TaskConflictPolicy.REJECT, spec.getConflictPolicy());
    }

    @Test
    void theRunConditionsFollowTheTask() {
        Autosave task = new Autosave("world");
        TaskSpec spec = task.toSpec();

        assertTrue(all(spec.getConditions()));
        task.active = false;
        assertFalse(all(spec.getConditions()));
        assertFalse(any(spec.getCancellationConditions()));
    }

    private static boolean all(Iterable<BooleanSupplier> tests) {
        for (BooleanSupplier test : tests) {
            if (!test.getAsBoolean()) {
                return false;
            }
        }
        return true;
    }

    private static boolean any(Iterable<BooleanSupplier> tests) {
        for (BooleanSupplier test : tests) {
            if (test.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }
}
