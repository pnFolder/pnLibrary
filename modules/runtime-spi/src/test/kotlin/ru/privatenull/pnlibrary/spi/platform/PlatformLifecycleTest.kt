package ru.privatenull.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

class PlatformLifecycleTest {
    @Test
    fun `binding is single shot until close`() {
        val lifecycle = PlatformLifecycle()

        lifecycle.beginBinding()
        assertThrows<IllegalStateException> { lifecycle.beginBinding() }
        lifecycle.endBinding()
        lifecycle.beginBinding()
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
        assertThrows<IllegalStateException> { lifecycle.beginBinding() }
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
}
