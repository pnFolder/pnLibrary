package org.pnfolder.pnlibrary.bungee.tasks;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks task scheduling and lifecycle tracking on the BungeeCord scheduler. */
class BungeeTaskAdapterTest {

    @Test
    void adapterForwardsRequestAndCancelsNativeHandleOnce() {
        AtomicReference<PlatformTaskRequest> seen = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        BungeeTaskAdapter adapter = new BungeeTaskAdapter(request -> {
            seen.set(request);
            return (PlatformTaskHandle) () -> cancelled.compareAndSet(false, true);
        });
        PlatformTaskRequest request = new PlatformTaskRequest(TaskExecution.Kind.ENTITY, "player",
                Duration.ofSeconds(2), Duration.ofSeconds(3), () -> { });

        PlatformTaskHandle handle = adapter.buildTask().from(request).schedule();

        assertEquals(request.getExecutionKind(), seen.get().getExecutionKind());
        assertEquals(request.getTarget(), seen.get().getTarget());
        assertEquals(request.getDelay(), seen.get().getDelay());
        assertEquals(request.getInterval(), seen.get().getInterval());
        assertTrue(handle.cancel());
        assertFalse(handle.cancel());
        assertTrue(cancelled.get());
    }

    @Test
    void completedOneShotIsReleasedBeforeAdapterClose() {
        AtomicReference<Runnable> callback = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        BungeeTaskAdapter adapter = new BungeeTaskAdapter(request -> {
            callback.set(request.getCallback());
            return (PlatformTaskHandle) () -> cancelled.compareAndSet(false, true);
        });
        adapter.buildTask().from(
                new PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, () -> { })).schedule();

        callback.get().run();
        adapter.close();

        assertFalse(cancelled.get());
    }
}
