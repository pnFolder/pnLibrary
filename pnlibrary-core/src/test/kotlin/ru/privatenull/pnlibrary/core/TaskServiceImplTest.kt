package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.core.tasks.TaskServiceImpl


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.platform.PlatformType
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.Supplier

class TaskServiceImplTest {
    @Test
    fun `scope cancellation prevents delayed work`() {
        val service = TaskServiceImpl(ImmediatePlatform())
        val scope = service.scope(Any())
        val calls = AtomicInteger()
        scope.later(Duration.ofMillis(100), Runnable { calls.incrementAndGet() })

        scope.close()
        Thread.sleep(180)

        assertEquals(0, calls.get())
        service.close()
    }

    @Test
    fun `entity work uses reply dispatcher`() {
        val platform = ImmediatePlatform()
        val service = TaskServiceImpl(platform)
        val latch = CountDownLatch(1)
        service.scope(Any()).entity("player", Runnable { latch.countDown() })

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(1, platform.replyCalls.get())
        assertEquals(0, platform.globalCalls.get())
        service.close()
    }

    @Test
    fun `repeating handle stops future executions`() {
        val service = TaskServiceImpl(ImmediatePlatform())
        val calls = AtomicInteger()
        val handle = service.scope(Any()).repeatAsync(
            Duration.ZERO, Duration.ofMillis(20), Runnable { calls.incrementAndGet() })
        Thread.sleep(90)
        handle.cancel()
        val stoppedAt = calls.get()
        Thread.sleep(80)

        assertTrue(stoppedAt >= 2)
        assertEquals(stoppedAt, calls.get())
        assertTrue(handle.isCancelled)
        service.close()
    }

    @Test
    fun `closing scope suppresses queued async continuation`() {
        val platform = QueuedPlatform()
        val service = TaskServiceImpl(platform)
        val scope = service.scope(Any())
        val calls = AtomicInteger()
        scope.asyncThen(
            Supplier { "loaded" },
            Consumer { calls.incrementAndGet() },
            Consumer { calls.incrementAndGet() },
        )
        assertTrue(platform.dispatched.await(1, TimeUnit.SECONDS))

        scope.close()
        platform.queued.get().run()

        assertEquals(0, calls.get())
        service.close()
    }

    private class ImmediatePlatform : PlatformAdapter {
        val globalCalls = AtomicInteger()
        val replyCalls = AtomicInteger()
        override val type = PlatformType.BUKKIT
        override val id = "test"
        override fun details(): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) { globalCalls.incrementAndGet(); task.run() }
        override fun executeReply(recipient: Any, task: Runnable) { replyCalls.incrementAndGet(); task.run() }
        override fun close() {}
    }

    private class QueuedPlatform : PlatformAdapter {
        val dispatched = CountDownLatch(1)
        val queued = AtomicReference<Runnable>()
        override val type = PlatformType.BUKKIT
        override val id = "queued"
        override fun details(): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) { queued.set(task); dispatched.countDown() }
        override fun executeReply(recipient: Any, task: Runnable) = executeGlobal(task)
        override fun close() = Unit
    }
}
