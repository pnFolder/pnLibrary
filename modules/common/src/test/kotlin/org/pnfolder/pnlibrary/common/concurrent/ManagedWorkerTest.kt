package org.pnfolder.pnlibrary.common.concurrent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ManagedWorkerTest {
    @Test fun `close drains all accepted operations`() {
        val worker = ManagedWorker("drain-test")
        val operations = (1..50).map { n -> worker.submit { n } }
        worker.close()
        assertEquals((1..50).toList(), operations.map { it.join() })
        assertThrows<IllegalStateException> { worker.submit { 1 } }
    }

    @Test fun `queue overflow and close timeout settle every promise`() {
        val worker = ManagedWorker("bounded-test", capacity = 1, closeTimeout = Duration.ofMillis(100))
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val running = worker.submit { started.countDown(); release.await(); 1 }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val queued = worker.submit { 2 }
        val rejected = worker.submit { 3 }
        try {
            assertTrue(rejected.isCompletedExceptionally)
            worker.close()
            assertTrue(running.isDone)
            assertTrue(queued.isCompletedExceptionally)
        } finally { release.countDown(); worker.close() }
    }

    @Test fun `a callback may close its own worker without deadlocking`() {
        val worker = ManagedWorker("callback-test")
        val finished = worker.submit { worker.close(); 7 }
        assertEquals(7, finished.get(5, TimeUnit.SECONDS))
        worker.close()
    }
}
