package org.pnfolder.pnlibrary.spi.platform;

import org.pnfolder.pnlibrary.spi.platform.lifecycle.PlatformLifecycle;
import org.pnfolder.pnlibrary.spi.platform.lifecycle.StartupGate;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskBuilder;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformLifecycleTest {
    @Test
    void bindingIsSingleShotUntilClose() {
        PlatformLifecycle lifecycle = new PlatformLifecycle();

        lifecycle.bind(() -> null);
        assertThrows(IllegalStateException.class, () -> lifecycle.bind(() -> null));
        assertTrue(lifecycle.close());
        assertFalse(lifecycle.close());
        assertThrows(IllegalStateException.class, lifecycle::checkOpen);
    }

    @Test
    void failedBindingRollsBackBoundState() {
        PlatformLifecycle lifecycle = new PlatformLifecycle();

        assertThrows(IllegalArgumentException.class, () -> lifecycle.bind(() -> {
            throw new IllegalArgumentException("registration failed");
        }));

        lifecycle.bind(() -> "registered");
        assertThrows(IllegalStateException.class, () -> lifecycle.bind(() -> null));
    }

    @Test
    void closeWaitsForAnActiveBindingTransaction() throws Exception {
        PlatformLifecycle lifecycle = new PlatformLifecycle();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> binding = executor.submit(() -> lifecycle.bind(() -> {
                entered.countDown();
                try {
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    throw new IllegalStateException(interrupted);
                }
                return null;
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            Future<Boolean> closing = executor.submit((Callable<Boolean>) lifecycle::close);

            assertTrue(!closing.isDone());
            release.countDown();
            binding.get(2, TimeUnit.SECONDS);
            assertTrue(closing.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
            lifecycle.close();
        }
    }

    @Test
    void closeReleasesTrackedResourcesInReverseOrder() {
        PlatformLifecycle lifecycle = new PlatformLifecycle();
        List<String> closed = new CopyOnWriteArrayList<>();

        lifecycle.bind(() -> {
            lifecycle.track((AutoCloseable) () -> closed.add("first"));
            return lifecycle.track((AutoCloseable) () -> closed.add("second"));
        });

        assertTrue(lifecycle.close());
        assertEquals(Arrays.asList("second", "first"), closed);
    }

    @Test
    void failedBindingReleasesResourcesRegisteredBeforeFailure() {
        PlatformLifecycle lifecycle = new PlatformLifecycle();
        AtomicBoolean closed = new AtomicBoolean();

        assertThrows(IllegalArgumentException.class, () -> lifecycle.bind(() -> {
            lifecycle.track((AutoCloseable) () -> closed.set(true));
            throw new IllegalArgumentException("registration failed");
        }));

        assertTrue(closed.get());
        lifecycle.bind(() -> null);
    }

    @Test
    void failedBindingPreservesRegistrationFailureWhenCleanupAlsoFails() {
        PlatformLifecycle lifecycle = new PlatformLifecycle();
        IllegalArgumentException registrationFailure = new IllegalArgumentException("registration failed");
        IllegalStateException cleanupFailure = new IllegalStateException("cleanup failed");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> lifecycle.bind(() -> {
            lifecycle.track((AutoCloseable) () -> {
                throw cleanupFailure;
            });
            throw registrationFailure;
        }));

        assertSame(registrationFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(cleanupFailure, thrown.getSuppressed()[0]);
        lifecycle.bind(() -> null);
    }

    @Test
    void closeRunsPostCloseCleanupWhenTrackedResourceFails() {
        PlatformLifecycle lifecycle = new PlatformLifecycle();
        AtomicBoolean cleaned = new AtomicBoolean();
        IllegalStateException cleanupFailure = new IllegalStateException("resource close failed");

        lifecycle.track((AutoCloseable) () -> {
            throw cleanupFailure;
        });

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> lifecycle.close(() -> cleaned.set(true)));

        assertSame(cleanupFailure, thrown);
        assertTrue(cleaned.get());
        assertFalse(lifecycle.close());
    }

    @Test
    void nestedShutdownFailsBindingAndAllowsRetry() {
        PlatformLifecycle lifecycle = new PlatformLifecycle();
        assertThrows(IllegalStateException.class, () -> lifecycle.bind(lifecycle::close));
        assertFalse(lifecycle.isClosed());
        lifecycle.bind(() -> null);
        assertTrue(lifecycle.close());
    }

    @Test
    void closedLifecycleRejectsFutureBinding() {
        PlatformLifecycle lifecycle = new PlatformLifecycle();

        assertTrue(lifecycle.close());

        assertThrows(IllegalStateException.class, () -> lifecycle.bind(() -> null));
        assertThrows(IllegalStateException.class, () -> lifecycle.track((AutoCloseable) () -> { }));
    }

    @Test
    void adapterShutdownReleasesRegistrationsStartupWorkTasksAndLogObservationInOrder() {
        PlatformLifecycle lifecycle = new PlatformLifecycle();
        List<String> steps = new CopyOnWriteArrayList<>();
        StartupGate startup = new StartupGate(lifecycle, task -> steps.add("startup task ran"));
        PlatformTaskAdapter tasks = new PlatformTaskAdapter() {
            @Override
            public PlatformTaskBuilder buildTask() {
                return new PlatformTaskBuilder(request -> () -> true);
            }

            @Override
            public void close() {
                steps.add("tasks");
            }
        };
        lifecycle.track((AutoCloseable) () -> steps.add("registration"));
        startup.whenReady(() -> { });

        assertTrue(lifecycle.closeAdapter(startup, tasks, () -> steps.add("logs")));
        startup.markReady();

        assertEquals(Arrays.asList("registration", "tasks", "logs"), steps);
        assertFalse(lifecycle.closeAdapter(startup, tasks, () -> steps.add("logs")));
    }

    @Test
    void adapterShutdownStopsLogObservationWhenTaskCancellationFails() {
        PlatformLifecycle lifecycle = new PlatformLifecycle();
        AtomicBoolean observing = new AtomicBoolean(true);
        IllegalStateException failure = new IllegalStateException("cancel failed");
        PlatformTaskAdapter tasks = new PlatformTaskAdapter() {
            @Override
            public PlatformTaskBuilder buildTask() {
                return new PlatformTaskBuilder((PlatformTaskRequest request) -> () -> true);
            }

            @Override
            public void close() {
                throw failure;
            }
        };

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> lifecycle.closeAdapter(null, tasks, () -> observing.set(false)));

        assertSame(failure, thrown);
        assertFalse(observing.get());
        assertTrue(lifecycle.isClosed());
    }
}
