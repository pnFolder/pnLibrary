package org.pnfolder.pnlibrary.common.concurrent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedWorkerTest {
    @Test
    void closeDrainsAllAcceptedOperations() throws Exception {
        ManagedWorker worker = new ManagedWorker("drain-test");
        List<CompletableFuture<Integer>> operations = new ArrayList<>();
        for (int n = 1; n <= 50; n++) {
            int value = n;
            operations.add(worker.submit(() -> value));
        }
        worker.close();
        List<Integer> results = new ArrayList<>();
        for (CompletableFuture<Integer> operation : operations) {
            results.add(operation.join());
        }
        List<Integer> expected = new ArrayList<>();
        for (int n = 1; n <= 50; n++) {
            expected.add(n);
        }
        assertEquals(expected, results);
        assertThrows(IllegalStateException.class, () -> worker.submit(() -> 1));
    }

    @Test
    void queueOverflowAndCloseTimeoutSettleEveryPromise() throws Exception {
        ManagedWorker worker = new ManagedWorker("bounded-test", 1, 1, Duration.ofMillis(100));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Integer> running = worker.submit(() -> {
            started.countDown();
            release.await();
            return 1;
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        CompletableFuture<Integer> queued = worker.submit(() -> 2);
        CompletableFuture<Integer> rejected = worker.submit(() -> 3);
        try {
            assertTrue(rejected.isCompletedExceptionally());
            worker.close();
            assertTrue(running.isDone());
            assertTrue(queued.isCompletedExceptionally());
        } finally {
            release.countDown();
            worker.close();
        }
    }

    @Test
    void aCallbackMayCloseItsOwnWorkerWithoutDeadlocking() throws Exception {
        ManagedWorker worker = new ManagedWorker("callback-test");
        CompletableFuture<Integer> finished = worker.submit(() -> {
            worker.close();
            return 7;
        });
        assertEquals(7, finished.get(5, TimeUnit.SECONDS));
        worker.close();
    }
}
