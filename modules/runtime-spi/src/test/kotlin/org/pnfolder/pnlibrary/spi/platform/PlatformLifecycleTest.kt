package org.pnfolder.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlatformLifecycleTest {
    @Test
    fun `binding is single shot until close`() {
        val lifecycle = PlatformLifecycle()

        lifecycle.bind { Unit }
        assertThrows<IllegalStateException> { lifecycle.bind { Unit } }
        assertTrue(lifecycle.close())
        assertFalse(lifecycle.close())
        assertThrows<IllegalStateException> { lifecycle.checkOpen() }
    }

    @Test
    fun `failed binding rolls back bound state`() {
        val lifecycle = PlatformLifecycle()

        assertThrows<IllegalArgumentException> {
            lifecycle.bind {
                throw IllegalArgumentException("registration failed")
            }
        }

        lifecycle.bind { "registered" }
        assertThrows<IllegalStateException> { lifecycle.bind { Unit } }
    }

    @Test
    fun `close waits for an active binding transaction`() {
        val lifecycle = PlatformLifecycle()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val binding = executor.submit {
                lifecycle.bind {
                    entered.countDown()
                    check(release.await(2, TimeUnit.SECONDS))
                }
            }
            check(entered.await(2, TimeUnit.SECONDS))
            val closing = executor.submit<Boolean> { lifecycle.close() }

            assertTrue(!closing.isDone)
            release.countDown()
            binding.get(2, TimeUnit.SECONDS)
            assertTrue(closing.get(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            executor.shutdownNow()
            lifecycle.close()
        }
    }

    @Test
    fun `close releases tracked resources in reverse order`() {
        val lifecycle = PlatformLifecycle()
        val closed = CopyOnWriteArrayList<String>()

        lifecycle.bind {
            lifecycle.track(AutoCloseable { closed += "first" })
            lifecycle.track(AutoCloseable { closed += "second" })
        }

        assertTrue(lifecycle.close())
        assertTrue(closed == listOf("second", "first"))
    }

    @Test
    fun `failed binding releases resources registered before failure`() {
        val lifecycle = PlatformLifecycle()
        var closed = false

        assertThrows<IllegalArgumentException> {
            lifecycle.bind {
                lifecycle.track(AutoCloseable { closed = true })
                throw IllegalArgumentException("registration failed")
            }
        }

        assertTrue(closed)
        lifecycle.bind { Unit }
    }

    @Test
    fun `failed binding preserves registration failure when cleanup also fails`() {
        val lifecycle = PlatformLifecycle()
        val registrationFailure = IllegalArgumentException("registration failed")
        val cleanupFailure = IllegalStateException("cleanup failed")

        val thrown = assertThrows<IllegalArgumentException> {
            lifecycle.bind {
                lifecycle.track(AutoCloseable { throw cleanupFailure })
                throw registrationFailure
            }
        }

        assertTrue(thrown === registrationFailure)
        assertTrue(thrown.suppressed.single() === cleanupFailure)
        lifecycle.bind { Unit }
    }

    @Test
    fun `close runs post-close cleanup when tracked resource fails`() {
        val lifecycle = PlatformLifecycle()
        var cleaned = false
        val cleanupFailure = IllegalStateException("resource close failed")

        lifecycle.track(AutoCloseable { throw cleanupFailure })

        val thrown = assertThrows<IllegalStateException> {
            lifecycle.close { cleaned = true }
        }

        assertTrue(thrown === cleanupFailure)
        assertTrue(cleaned)
        assertFalse(lifecycle.close())
    }

    @Test
    fun `nested shutdown fails binding and allows retry`() {
        val lifecycle = PlatformLifecycle()
        assertThrows<IllegalStateException> {
            lifecycle.bind { lifecycle.close() }
        }
        assertFalse(lifecycle.isClosed)
        lifecycle.bind { Unit }
        assertTrue(lifecycle.close())
    }

    @Test
    fun `closed lifecycle rejects future binding`() {
        val lifecycle = PlatformLifecycle()

        assertTrue(lifecycle.close())

        assertThrows<IllegalStateException> {
            lifecycle.bind { Unit }
        }
        assertThrows<IllegalStateException> {
            lifecycle.track(AutoCloseable { })
        }
    }

    @Test
    fun `adapter shutdown releases registrations, startup work, tasks and log observation in order`() {
        val lifecycle = PlatformLifecycle()
        val steps = CopyOnWriteArrayList<String>()
        val startup = StartupGate(lifecycle) { steps += "startup task ran" }
        val tasks = object : PlatformTaskAdapter {
            override fun schedule(request: PlatformTaskRequest) = PlatformTaskHandle { true }
            override fun close() { steps += "tasks" }
        }
        lifecycle.track(AutoCloseable { steps += "registration" })
        startup.whenReady(Runnable {})

        assertTrue(lifecycle.closeAdapter(startup, tasks) { steps += "logs" })
        startup.markReady()

        assertEquals(listOf("registration", "tasks", "logs"), steps)
        assertFalse(lifecycle.closeAdapter(startup, tasks) { steps += "logs" })
    }

    @Test
    fun `adapter shutdown stops log observation when task cancellation fails`() {
        val lifecycle = PlatformLifecycle()
        var observing = true
        val failure = IllegalStateException("cancel failed")
        val tasks = object : PlatformTaskAdapter {
            override fun schedule(request: PlatformTaskRequest) = PlatformTaskHandle { true }
            override fun close() = throw failure
        }

        val thrown = assertThrows<IllegalStateException> {
            lifecycle.closeAdapter(null, tasks) { observing = false }
        }

        assertTrue(thrown === failure)
        assertFalse(observing)
        assertTrue(lifecycle.isClosed)
    }
}
