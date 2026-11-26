package org.pnfolder.pnlibrary.spi.tasks;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformTaskAdapterTest {
    @Test
    void entityRequestRequiresATarget() {
        assertThrows(IllegalArgumentException.class, () ->
                new PlatformTaskRequest(TaskExecution.Kind.ENTITY, null, Duration.ZERO, null, () -> { }));
    }

    @Test
    void unsupportedAdapterRejectsRegistration() {
        assertThrows(UnsupportedOperationException.class, () ->
                UnsupportedPlatformTaskAdapter.INSTANCE.buildTask().from(
                        new PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, () -> { })
                ).schedule());
    }

    @Test
    void handleCreatedWhileClosingIsCancelledAfterRegistration() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger cancellations = new AtomicInteger();
        TrackingPlatformTaskAdapter adapter = new TrackingPlatformTaskAdapter() {
            @Override
            protected PlatformTaskHandle scheduleNative(PlatformTaskRequest request) {
                entered.countDown();
                try {
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    throw new IllegalStateException(interrupted);
                }
                return () -> {
                    cancellations.incrementAndGet();
                    return true;
                };
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<PlatformTaskHandle> future = executor.submit(() -> adapter.buildTask().from(
                    new PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, () -> { })).schedule());
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            adapter.close();
            release.countDown();

            future.get(2, TimeUnit.SECONDS);
            assertEquals(1, cancellations.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
            adapter.close();
        }
    }
}
