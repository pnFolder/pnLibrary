package org.pnfolder.pnlibrary.velocity.tasks;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks task scheduling, entity-task warnings and shutdown on the Velocity scheduler. */
class VelocityTaskAdapterTest {
    private static final Consumer<String> WARN = message -> { };

    private static PlatformTaskRequest global() {
        return new PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, () -> { });
    }

    @Test
    void taskCreatedDuringShutdownIsCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<VelocityTaskAdapter> adapter = new AtomicReference<>();
        adapter.set(new VelocityTaskAdapter(WARN, request -> {
            adapter.get().close();
            return () -> cancelled.compareAndSet(false, true);
        }));

        adapter.get().buildTask().from(global()).schedule();

        assertTrue(cancelled.get());
    }

    @Test
    void closeAttemptsEveryCancellationEvenIfOneFails() {
        AtomicInteger scheduled = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        VelocityTaskAdapter adapter = new VelocityTaskAdapter(WARN, request -> {
            int index = scheduled.getAndIncrement();
            return () -> {
                cancellations.incrementAndGet();
                if (index == 0) {
                    throw new IllegalStateException("cancel failed");
                }
                return true;
            };
        });
        for (int i = 0; i < 2; i++) {
            adapter.buildTask().from(global()).schedule();
        }

        assertThrows(IllegalStateException.class, adapter::close);
        assertEquals(2, cancellations.get());
        adapter.close();
        assertEquals(2, cancellations.get());
    }

    @Test
    void entitySchedulingRunsOnTheProxySchedulerAndWarnsOnEveryCall() {
        List<String> warnings = new ArrayList<>();
        AtomicInteger scheduled = new AtomicInteger();
        VelocityTaskAdapter adapter = new VelocityTaskAdapter(warnings::add, request -> {
            scheduled.incrementAndGet();
            return () -> true;
        });
        PlatformTaskRequest request = new PlatformTaskRequest(TaskExecution.Kind.ENTITY, new Object(),
                Duration.ZERO, null, () -> { });

        adapter.buildTask().from(request).schedule();
        adapter.buildTask().from(request).schedule();

        assertEquals(2, scheduled.get());
        assertEquals(2, warnings.size());
        for (String warning : warnings) {
            assertTrue(warning.contains("Entity-bound"));
        }
    }

    @Test
    void adapterForwardsNativeDurationRequestAndClosesRegistrations() {
        AtomicReference<PlatformTaskRequest> seen = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        VelocityTaskAdapter adapter = new VelocityTaskAdapter(WARN, request -> {
            seen.set(request);
            return (PlatformTaskHandle) () -> cancelled.compareAndSet(false, true);
        });
        PlatformTaskRequest request = new PlatformTaskRequest(TaskExecution.Kind.ASYNC, null,
                Duration.ofMillis(5), null, () -> { });

        adapter.buildTask().from(request).schedule();
        adapter.close();

        assertEquals(request.getExecutionKind(), seen.get().getExecutionKind());
        assertEquals(request.getDelay(), seen.get().getDelay());
        assertEquals(request.getInterval(), seen.get().getInterval());
        assertTrue(cancelled.get());
        assertThrows(IllegalStateException.class, () -> adapter.buildTask().from(request).schedule());
    }

    @Test
    void completedOneShotIsReleasedBeforeAdapterClose() {
        AtomicReference<Runnable> callback = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        VelocityTaskAdapter adapter = new VelocityTaskAdapter(WARN, request -> {
            callback.set(request.getCallback());
            return (PlatformTaskHandle) () -> cancelled.compareAndSet(false, true);
        });

        adapter.buildTask().from(global()).schedule();
        callback.get().run();
        adapter.close();

        assertFalse(cancelled.get());
    }
}
