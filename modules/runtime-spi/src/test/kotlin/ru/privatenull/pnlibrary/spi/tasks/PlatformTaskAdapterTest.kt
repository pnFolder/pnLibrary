package ru.privatenull.pnlibrary.spi.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlatformTaskAdapterTest {
    @Test
    fun `entity request requires a target`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformTaskRequest(TaskExecution.Kind.ENTITY, null, Duration.ZERO, null, Runnable {})
        }
    }

    @Test
    fun `unsupported adapter rejects registration`() {
        assertThrows(UnsupportedOperationException::class.java) {
            UnsupportedPlatformTaskAdapter.schedule(
                PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {}),
            )
        }
    }

    @Test
    fun `handle created while closing is cancelled after registration`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var cancellations = 0
        val adapter = object : TrackingPlatformTaskAdapter() {
            override fun scheduleNative(request: PlatformTaskRequest): PlatformTaskHandle {
                entered.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                return PlatformTaskHandle { cancellations++; true }
            }
        }
        val executor = Executors.newSingleThreadExecutor()

        try {
            val future = executor.submit<PlatformTaskHandle> {
                adapter.schedule(PlatformTaskRequest(TaskExecution.Kind.GLOBAL, null, Duration.ZERO, null, Runnable {}))
            }
            check(entered.await(2, TimeUnit.SECONDS))
            adapter.close()
            release.countDown()

            future.get(2, TimeUnit.SECONDS)
            assertEquals(1, cancellations)
        } finally {
            release.countDown()
            executor.shutdownNow()
            adapter.close()
        }
    }
}
