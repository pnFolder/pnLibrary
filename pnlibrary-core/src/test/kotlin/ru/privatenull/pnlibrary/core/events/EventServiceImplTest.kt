package ru.privatenull.pnlibrary.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.events.CancellablePnEvent
import ru.privatenull.pnlibrary.api.events.EventPriority
import ru.privatenull.pnlibrary.api.events.PnEvent
import java.util.function.Consumer

class EventServiceImplTest {
    @Test
    fun `listeners run by priority and registration order`() {
        val calls = mutableListOf<String>()
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(Any())
            scope.subscribe(TestEvent::class.java, EventPriority.HIGH, Consumer { calls += "high" })
            scope.subscribe(PnEvent::class.java, EventPriority.LOW, Consumer { calls += "base" })
            scope.subscribe(TestEvent::class.java, EventPriority.LOW, Consumer { calls += "low" })

            val result = events.publish(TestEvent())

            assertEquals(listOf("base", "low", "high"), calls)
            assertEquals(3, result.delivered)
            assertEquals(0, result.failed)
        }
    }

    @Test
    fun `cancelled events skip only listeners that request it`() {
        val calls = mutableListOf<String>()
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(Any())
            scope.subscribe(CancelEvent::class.java, EventPriority.LOW, Consumer {
                calls += "cancel"
                it.isCancelled = true
            })
            scope.subscribe(CancelEvent::class.java, EventPriority.NORMAL, true, Consumer { calls += "skipped" })
            scope.subscribe(CancelEvent::class.java, EventPriority.MONITOR, false, Consumer { calls += "monitor" })

            val result = scope.publish(CancelEvent())

            assertEquals(listOf("cancel", "monitor"), calls)
            assertEquals(2, result.delivered)
            assertEquals(1, result.skipped)
            assertTrue(result.cancelled)
        }
    }

    @Test
    fun `listener failure is isolated and reported`() {
        val errors = mutableListOf<Throwable>()
        var completed = false
        EventServiceImpl { _, _, error -> errors += error }.use { events ->
            val scope = events.scope(Any())
            scope.subscribe(TestEvent::class.java, Consumer { error("broken") })
            scope.subscribe(TestEvent::class.java, Consumer { completed = true })

            val result = events.publish(TestEvent())

            assertTrue(completed)
            assertEquals(1, result.delivered)
            assertEquals(1, result.failed)
            assertEquals("broken", errors.single().message)
        }
    }

    @Test
    fun `owner scope closes all of its subscriptions`() {
        val owner = Any()
        val otherOwner = Any()
        var ownerCalls = 0
        var otherCalls = 0
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(owner)
            assertSame(scope, events.scope(owner))
            scope.subscribe(TestEvent::class.java, Consumer { ownerCalls++ })
            events.scope(otherOwner).subscribe(TestEvent::class.java, Consumer { otherCalls++ })

            events.close(owner)
            events.publish(TestEvent())

            assertTrue(scope.isClosed)
            assertEquals(0, ownerCalls)
            assertEquals(1, otherCalls)
            assertThrows(IllegalStateException::class.java) {
                scope.subscribe(TestEvent::class.java, Consumer { })
            }
        }
    }

    @Test
    fun `closed service rejects further work`() {
        val events = EventServiceImpl { _, _, _ -> }
        events.close()

        assertThrows(IllegalStateException::class.java) { events.scope(Any()) }
        assertThrows(IllegalStateException::class.java) { events.publish(TestEvent()) }
    }

    private open class TestEvent : PnEvent

    private class CancelEvent : CancellablePnEvent {
        override var isCancelled: Boolean = false
    }
}
