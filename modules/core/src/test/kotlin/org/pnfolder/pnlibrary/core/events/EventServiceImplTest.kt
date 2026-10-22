package org.pnfolder.pnlibrary.core.events

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.events.*
import org.pnfolder.pnlibrary.api.plugin.PluginId
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
    fun `denied result is visible immediately and skips ignoreCancelled listeners`() {
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(PluginId.of("test"))
            scope.subscribe(ResultEvent::class.java, EventPriority.LOW, Consumer {
                it.value = "handled"
                it.result = ResultedEvent.ComponentResult.denied(Component.text("nope"))
            })
            scope.subscribe(ResultEvent::class.java, EventPriority.NORMAL, true, Consumer {
                error("denied event must be skipped")
            })
            val event = ResultEvent()
            val returned = scope.callEvent(event)
            assertSame(event, returned)
            assertEquals("handled", event.value)
            assertFalse(event.isAllowed)
            assertEquals(Component.text("nope"), event.denyReason)
        }
    }

    @Test
    fun `allowed result keeps ignoreCancelled listeners running`() {
        var ran = false
        EventServiceImpl { _, _, _ -> }.use { events ->
            events.scope(PluginId.of("test")).subscribe(ResultEvent::class.java, EventPriority.NORMAL, true, Consumer {
                ran = true
            })
            val event = ResultEvent()
            assertTrue(event.isAllowed)
            events.callEvent(event)
            assertTrue(ran)
            assertNull(event.denyReason)
        }
    }

    @Test
    fun `typed subscription receives every resulted event`() {
        var seen = 0
        EventServiceImpl { _, _, _ -> }.use { events ->
            events.scope(PluginId.of("test")).subscribe(
                ResultedEvent::class.java as Class<ResultedEvent<*>>,
                Consumer { seen++ },
            )
            events.callEvent(ResultEvent())
            events.callEvent(ResultEvent())
            assertEquals(2, seen)
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

    @Test
    fun `builder applies priority and ignoreCancelled then registers on listen`() {
        val calls = mutableListOf<String>()
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(PluginId.of("test"))
            scope.on<ResultEvent>().priority(EventPriority.LOWEST).listen {
                it.result = ResultedEvent.ComponentResult.denied(Component.text("no"))
            }
            scope.on<ResultEvent>().priority(EventPriority.LOW).listen { calls += "low" }
            scope.on<ResultEvent>().ignoreCancelled().listen { calls += "skipped-once-denied" }
            scope.on<ResultEvent>().priority(EventPriority.HIGH).listen { calls += "high" }

            events.callEvent(ResultEvent())
            assertEquals(listOf("low", "high"), calls)
        }
    }

    @Test
    fun `builder registers nothing until listen is called`() {
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(PluginId.of("test"))
            scope.on<ResultEvent>().priority(EventPriority.HIGH).ignoreCancelled()
            var ran = false
            events.callEvent(ResultEvent())
            assertFalse(ran)
            scope.on<ResultEvent>().listen { ran = true }
            events.callEvent(ResultEvent())
            assertTrue(ran)
        }
    }

    @Test
    fun `listeners added or removed after a warm call take effect on the next call`() {
        val calls = mutableListOf<String>()
        EventServiceImpl { _, _, _ -> }.use { events ->
            val scope = events.scope(PluginId.of("test"))
            val first = scope.subscribe(TestEvent::class.java, EventPriority.NORMAL, Consumer { calls += "first" })
            events.callEvent(TestEvent())
            assertEquals(listOf("first"), calls)

            scope.subscribe(TestEvent::class.java, EventPriority.LOW, Consumer { calls += "earlier" })
            events.callEvent(TestEvent())
            assertEquals(listOf("first", "earlier", "first"), calls)

            first.close()
            events.callEvent(TestEvent())
            assertEquals(listOf("first", "earlier", "first", "earlier"), calls)
        }
    }

    @Test
    fun `closing a plugin scope removes its listeners from an already cached dispatch`() {
        var ran = 0
        EventServiceImpl { _, _, _ -> }.use { events ->
            val id = PluginId.of("test")
            events.scope(id).subscribe(TestEvent::class.java, Consumer { ran++ })
            events.callEvent(TestEvent())
            events.unregisterAll(id)
            events.callEvent(TestEvent())
            assertEquals(1, ran)
        }
    }

    @Test
    fun `an event nobody listens to dispatches without error and is not retained`() {
        EventServiceImpl { _, _, _ -> }.use { events ->
            repeat(1_000) { events.callEvent(TestEvent()) }
        }
    }

    private open class TestEvent : Event()
    private class ResultEvent : ResultedEvent<ResultedEvent.ComponentResult>() {
        override var result: ResultedEvent.ComponentResult = ResultedEvent.ComponentResult.allowed()
        var value = "initial"
    }

    private class AnnotatedListener(private val calls: MutableList<String>) : Listener {
        @EventHandler(priority = -250)
        private fun early(event: TestEvent) { calls += "early" }
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
