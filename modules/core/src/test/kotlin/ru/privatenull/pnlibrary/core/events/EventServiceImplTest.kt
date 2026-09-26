package ru.privatenull.pnlibrary.core.events

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.events.*
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.function.Consumer

class EventServiceImplTest {
    @Test
    fun `call returns only after listeners run in priority order`() {
        val calls = mutableListOf<String>()
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(PluginId.of("test"))
            scope.subscribe(TestEvent::class.java, EventPriority.HIGH, Consumer { calls += "high" })
            scope.subscribe(Event::class.java, EventPriority.LOW, Consumer { calls += "base" })
            scope.subscribe(TestEvent::class.java, EventPriority.LOW, Consumer { calls += "low" })
            events.callEvent(TestEvent())
            assertEquals(listOf("base", "low", "high"), calls)
        }
    }

    @Test
    fun `mutable cancellation state is available immediately`() {
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(PluginId.of("test"))
            scope.subscribe(MutableEvent::class.java, EventPriority.LOW, Consumer {
                it.value = "handled"
                it.isCancelled = true
            })
            scope.subscribe(MutableEvent::class.java, EventPriority.NORMAL, true, Consumer {
                error("cancelled event must be skipped")
            })
            val event = MutableEvent()
            val returned = scope.callEvent(event)
            assertSame(event, returned)
            assertEquals("handled", event.value)
            assertTrue(event.isCancelled)
        }
    }

    @Test
    fun `annotated handlers register in order and close together`() {
        val calls = mutableListOf<String>()
        EventServiceImpl { _, _, _ -> }.use { events ->
            val registration = events.scope(PluginId.of("test")).register(AnnotatedListener(calls))
            events.callEvent(TestEvent())
            assertEquals(listOf("early", "normal"), calls)
            assertEquals(2, registration.handlerCount)
            registration.close()
            events.callEvent(TestEvent())
            assertEquals(2, calls.size)
        }
    }

    @Test
    fun `listener failure is logged and isolated`() {
        val errors = mutableListOf<Throwable>()
        var completed = false
        EventServiceImpl { _, _, error -> errors += error }.use { events ->
            val scope = events.scope(PluginId.of("test"))
            scope.subscribe(TestEvent::class.java, Consumer { error("broken") })
            scope.subscribe(TestEvent::class.java, Consumer { completed = true })
            events.callEvent(TestEvent())
            assertTrue(completed)
            assertEquals("broken", errors.single().message)
        }
    }

    @Test
    fun `scope and service lifecycle is enforced`() {
        val events = EventServiceImpl { _, _, _ -> }
        val id = PluginId.of("test")
        val scope = events.scope(id)
        assertSame(scope, events.scope(id))
        events.unregisterAll(id)
        assertTrue(scope.isClosed)
        assertThrows(IllegalStateException::class.java) {
            scope.subscribe(TestEvent::class.java, Consumer { })
        }
        events.close()
        assertThrows(IllegalStateException::class.java) { events.callEvent(TestEvent()) }
    }

    @Test
    fun `dispatch uses calling thread`() {
        val callingThread = Thread.currentThread()
        var listenerThread: Thread? = null
        EventServiceImpl { _, _, _ -> }.use { events ->
            events.scope(PluginId.of("test")).subscribe(TestEvent::class.java, Consumer {
                listenerThread = Thread.currentThread()
            })
            events.callEvent(TestEvent())
            assertSame(callingThread, listenerThread)
        }
    }

    @Test
    fun `invalid annotated signature fails during registration`() {
        EventServiceImpl { _, _, _ -> }.use { events ->
            assertThrows(IllegalArgumentException::class.java) {
                events.scope(PluginId.of("test")).register(InvalidListener())
            }
        }
    }

    private open class TestEvent : Event()
    private class MutableEvent : Event(), Cancellable {
        override var isCancelled = false
        var value = "initial"
    }

    private class AnnotatedListener(private val calls: MutableList<String>) : Listener {
        @EventHandler(priority = -250)
        private fun early(event: TestEvent) { calls += "early" }
        @EventHandler fun normal(event: TestEvent) { calls += "normal" }
    }

    private class InvalidListener : Listener {
        @EventHandler fun invalid() = Unit
    }
}
