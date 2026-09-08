package ru.privatenull.pnlibrary.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.events.Cancellable
import ru.privatenull.pnlibrary.api.events.Event
import ru.privatenull.pnlibrary.api.events.EventHandler
import ru.privatenull.pnlibrary.api.events.EventPriority
import ru.privatenull.pnlibrary.api.events.Listener
import java.util.function.Consumer

class EventServiceImplTest {
    @Test
    fun `listeners run by priority and registration order`() {
        val calls = mutableListOf<String>()
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(Any())
            scope.subscribe(TestEvent::class.java, EventPriority.HIGH, Consumer { calls += "high" })
            scope.subscribe(Event::class.java, EventPriority.LOW, Consumer { calls += "base" })
            scope.subscribe(TestEvent::class.java, EventPriority.LOW, Consumer { calls += "low" })

            val result = events.publish(TestEvent())

            assertEquals(listOf("base", "low", "high"), calls)
            assertEquals(3, result.delivered)
            assertEquals(0, result.failed)
        }
    }

    @Test
    fun `arbitrary numeric priorities fit between presets`() {
        val calls = mutableListOf<Int>()
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(Any())
            scope.subscribe(TestEvent::class.java, EventPriority.HIGH, Consumer { calls += 500 })
            scope.subscribe(TestEvent::class.java, 250, Consumer { calls += 250 })
            scope.subscribe(TestEvent::class.java, EventPriority.NORMAL, Consumer { calls += 0 })

            events.publish(TestEvent())

            assertEquals(listOf(0, 250, 500), calls)
        }
    }

    @Test
    fun `annotated listener registers all valid handler methods`() {
        val calls = mutableListOf<String>()
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(Any())
            val registration = scope.register(AnnotatedListener(calls))

            val result = events.publish(TestEvent())

            assertEquals(2, registration.handlerCount)
            assertEquals(listOf("early", "normal"), calls)
            assertEquals(2, result.delivered)

            registration.close()
            assertTrue(registration.isClosed)
            events.publish(TestEvent())
            assertEquals(2, calls.size)
        }
    }

    @Test
    fun `invalid annotated signature fails during registration`() {
        EventServiceImpl { _, _, _ -> }.use { events ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                events.scope(Any()).register(InvalidListener())
            }
            assertTrue(error.message.orEmpty().contains("exactly one parameter"))
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

    private open class TestEvent : Event()

    private class CancelEvent : Event(), Cancellable {
        override var isCancelled: Boolean = false
    }

    private class AnnotatedListener(private val calls: MutableList<String>) : Listener {
        @EventHandler(priority = -250)
        private fun early(event: TestEvent) {
            calls += "early"
        }

        @EventHandler
        fun normal(event: TestEvent) {
            calls += "normal"
        }
    }

    private class InvalidListener : Listener {
        @EventHandler
        fun invalid() = Unit
    }
}
