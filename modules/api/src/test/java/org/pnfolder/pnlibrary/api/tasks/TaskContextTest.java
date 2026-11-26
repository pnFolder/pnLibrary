package org.pnfolder.pnlibrary.api.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.info.TaskId;
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot;
import org.pnfolder.pnlibrary.api.tasks.run.TaskContext;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskSpec;

class TaskContextTest {
    private static final class FakeContext implements TaskContext {
        private final long runNumber;
        private final Instant scheduledAt;
        private final Instant startedAt;
        private final Duration interval;
        private final TaskId id = new TaskId("t");
        private final TaskExecution execution = TaskExecution.global();
        boolean cancelled;

        FakeContext(long runNumber) {
            this(runNumber, Instant.EPOCH, Instant.EPOCH, null);
        }

        FakeContext(long runNumber, Instant scheduledAt, Instant startedAt, Duration interval) {
            this.runNumber = runNumber;
            this.scheduledAt = scheduledAt;
            this.startedAt = startedAt;
            this.interval = interval;
        }

        @Override
        public TaskId getId() {
            return id;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getKey() {
            return null;
        }

        @Override
        public TaskId getParentId() {
            return null;
        }

        @Override
        public Set<String> getTags() {
            return Collections.emptySet();
        }

        @Override
        public TaskExecution.Kind getExecutionKind() {
            return TaskExecution.Kind.GLOBAL;
        }

        @Override
        public TaskExecution getExecution() {
            return execution;
        }

        @Override
        public Duration getInterval() {
            return interval;
        }

        @Override
        public long getRunNumber() {
            return runNumber;
        }

        @Override
        public long getSkippedCount() {
            return 0L;
        }

        @Override
        public Instant getScheduledAt() {
            return scheduledAt;
        }

        @Override
        public Instant getStartedAt() {
            return startedAt;
        }

        @Override
        public Instant getPreviousStartedAt() {
            return null;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public TaskHandle spawn(TaskSpec spec) {
            throw new IllegalStateException("not needed");
        }

        @Override
        public TaskSnapshot snapshot() {
            throw new IllegalStateException("not needed");
        }
    }

    @Test
    void runCountersAnswerFirstEveryAndCancelAfter() {
        assertTrue(new FakeContext(1).isFirstRun());
        assertFalse(new FakeContext(2).isFirstRun());
        assertTrue(new FakeContext(10).every(5));
        assertFalse(new FakeContext(11).every(5));
        assertThrows(IllegalArgumentException.class, () -> new FakeContext(1).every(0));

        FakeContext early = new FakeContext(2);
        assertFalse(early.cancelAfter(3));
        assertFalse(early.cancelled);
        FakeContext last = new FakeContext(3);
        assertTrue(last.cancelAfter(3));
        assertTrue(last.cancelled);
    }

    @Test
    void lagIsNeverNegativeAndRepeatingFollowsTheInterval() {
        assertEquals(Duration.ofMillis(300), new FakeContext(1, Instant.EPOCH, Instant.ofEpochMilli(300), null).getLag());
        assertEquals(Duration.ZERO, new FakeContext(1, Instant.ofEpochMilli(500), Instant.EPOCH, null).getLag());
        assertTrue(new FakeContext(1, Instant.EPOCH, Instant.EPOCH, Duration.ofSeconds(1)).isRepeating());
        assertFalse(new FakeContext(1).isRepeating());
    }
}
