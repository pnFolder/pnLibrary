package ru.privatenull.pnlibrary.core.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.events.Event
import ru.privatenull.pnlibrary.api.events.EventHandler
import ru.privatenull.pnlibrary.api.events.Listener
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.logging.MessageBox
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskService
import ru.privatenull.pnlibrary.api.updates.UpdateService
import ru.privatenull.pnlibrary.core.events.EventServiceImpl
import ru.privatenull.pnlibrary.core.testing.TestTaskService
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl
import java.lang.reflect.Proxy
import java.util.function.Supplier

class PluginRegistryImplTest {
    @Test
    fun `context is globally addressable by normalized plugin ID`() {
        val owner = Any()
        val taskScope = RecordingTaskScope(owner)
        val tasks = RecordingTaskService(taskScope)
        val events = EventServiceImpl(TestTaskService()) { _, _, _ -> }
        val metrics = RecordingMetricsService()
        val registry = PluginRegistryImpl(
            platform(),
            events,
            tasks,
            ServiceManagerImpl(),
            loggingService(),
            metrics,
            emptyProxy(DiagnosticsService::class.java),
            emptyProxy(UpdateService::class.java),
        )
        val listener = RecordingListener()

        val context = registry.register(owner, PluginId.of("pnClans")) {
            it.listener(listener).metrics(42, false)
        }

        assertSame(context, registry.get("PNCLANS"))
        assertEquals("pnclans", context.id.value)
        assertFalse(context.metrics.isEnabled)
        context.metrics.enable()
        assertTrue(context.metrics.isEnabled)
        assertEquals(42, metrics.lastProjectId)
        assertEquals(1, events.publish(TestEvent()).join().delivered)

        context.close()

        assertNull(registry.get("pnclans"))
        assertTrue(context.isClosed)
        assertEquals(0, events.publish(TestEvent()).join().delivered)
        assertTrue(taskScope.closed)
    }

    @Test
    fun `duplicate plugin ID is rejected`() {
        val owner = Any()
        val taskScope = RecordingTaskScope(owner)
        val tasks = RecordingTaskService(taskScope)
        val registry = PluginRegistryImpl(
            platform(),
            EventServiceImpl(TestTaskService()) { _, _, _ -> },
            tasks,
            ServiceManagerImpl(),
            loggingService(),
            RecordingMetricsService(),
            emptyProxy(DiagnosticsService::class.java),
            emptyProxy(UpdateService::class.java),
        )
        registry.register(owner, PluginId.of("example")) { }

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(owner, PluginId.of("EXAMPLE")) { }
        }
        registry.close()
    }

    @Test
    fun `one platform owner cannot be registered under two IDs`() {
        val owner = Any()
        val taskScope = RecordingTaskScope(owner)
        val registry = PluginRegistryImpl(
            platform(),
            EventServiceImpl(TestTaskService()) { _, _, _ -> },
            RecordingTaskService(taskScope),
            ServiceManagerImpl(),
            loggingService(),
            RecordingMetricsService(),
            emptyProxy(DiagnosticsService::class.java),
            emptyProxy(UpdateService::class.java),
        )
        registry.register(owner, "first") { }

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(owner, "second") { }
        }
        registry.close()
    }

    @Test
    fun `platform owner cleanup removes its global context`() {
        val owner = Any()
        val taskScope = RecordingTaskScope(owner)
        val registry = PluginRegistryImpl(
            platform(),
            EventServiceImpl(TestTaskService()) { _, _, _ -> },
            RecordingTaskService(taskScope),
            ServiceManagerImpl(),
            loggingService(),
            RecordingMetricsService(),
            emptyProxy(DiagnosticsService::class.java),
            emptyProxy(UpdateService::class.java),
        )
        val context = registry.register(owner, "example") { }

        registry.unregisterOwner(owner)

        assertTrue(context.isClosed)
        assertNull(registry.get("example"))
        assertTrue(taskScope.closed)
    }

    @Test
    fun `native registration resolves metadata and lifecycle messages are explicit`() {
        val owner = Any()
        val taskScope = RecordingTaskScope(owner)
        val logging = RecordingLoggingService()
        val registry = PluginRegistryImpl(
            platform(),
            EventServiceImpl(TestTaskService()) { _, _, _ -> },
            RecordingTaskService(taskScope),
            ServiceManagerImpl(),
            logging,
            RecordingMetricsService(),
            emptyProxy(DiagnosticsService::class.java),
            emptyProxy(UpdateService::class.java),
        )

        val context = registry.register(owner) { plugin ->
            plugin.metadata { metadata ->
                metadata.name("Custom Example").version("2.0.0").authors("Library Team")
            }
        }

        assertEquals("example", context.id.value)
        assertEquals("Custom Example", context.metadata.name)
        assertEquals("2.0.0", context.metadata.version)
        assertEquals("Library Team", context.metadata.authors)
        assertEquals(PlatformType.BUKKIT, context.metadata.platform)
        assertEquals("Paper", context.metadata.platformImplementation)
        assertEquals(0, logging.startupMessages)

        val operationMessage = context.messages.box("Cache refresh")
            .ok("Loaded", "25 entries")
        assertEquals(0, logging.genericMessages)
        operationMessage.show()
        assertEquals(1, logging.genericMessages)

        context.lifecycle.enabled().ok("Configuration", "loaded").show()

        assertEquals(1, logging.startupMessages)

        context.close()

        assertEquals(0, logging.shutdownMessages)

        context.lifecycle.disabled().ok("Storage", "saved: 7").show()

        assertEquals(1, logging.shutdownMessages)
    }

    private class RecordingListener : Listener {
        @EventHandler
        fun handle(event: TestEvent) = Unit
    }

    private class TestEvent : Event()

    private class RecordingMetricsService : MetricsService {
        var lastProjectId: Int? = null
        override fun open(owner: Any, projectId: Int): PluginMetrics {
            lastProjectId = projectId
            return NoopMetrics(projectId)
        }
    }

    private class NoopMetrics(override val projectId: Int) : PluginMetrics {
        override fun simplePie(id: String, value: Supplier<String?>) = this
        override fun advancedPie(id: String, values: Supplier<Map<String, Int>>) = this
        override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>) = this
        override fun singleLineChart(id: String, value: Supplier<Int>) = this
        override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>) = this
        override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>) = this
        override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>) = this
        override fun close() = Unit
    }

    private class RecordingTaskScope(owner: Any) {
        var closed = false
        val value: TaskScope = proxy(TaskScope::class.java) { methodName ->
            when (methodName) {
                "getOwner" -> owner
                "close", "cancelAll" -> { closed = true; Unit }
                else -> null
            }
        }
    }

    private class RecordingTaskService(private val scope: RecordingTaskScope) : TaskService {
        override fun scope(owner: Any): TaskScope = scope.value
        override fun close(owner: Any) { scope.value.close() }
        override fun close() { scope.value.close() }
    }

    private class RecordingLoggingService : LoggingService {
        var genericMessages = 0
        var startupMessages = 0
        var shutdownMessages = 0
        override fun logger(owner: Any, name: String): PnLogger = emptyProxy(PnLogger::class.java)
        override fun box(owner: Any, title: String): MessageBox =
            RecordingMessageBox { genericMessages++ }
        override fun box(owner: Any, name: String, version: String): MessageBox =
            RecordingMessageBox { startupMessages++ }
        override fun shutdownBox(owner: Any, title: String): MessageBox =
            RecordingMessageBox { shutdownMessages++ }
    }

    private class RecordingMessageBox(private val onShow: () -> Unit) : MessageBox {
        override fun ok(label: String, detail: String) = this
        override fun warn(label: String, detail: String) = this
        override fun skip(label: String, detail: String) = this
        override fun fail(label: String, detail: String, error: Throwable?) = this
        override fun show() = onShow()
    }

    private companion object {
        fun platform(): PlatformAdapter = proxy(PlatformAdapter::class.java) { methodName ->
            when (methodName) {
                "getType" -> PlatformType.BUKKIT
                "getImplementationName" -> "Paper"
                "ownerDetails" -> mapOf(
                    "id" to "example",
                    "name" to "Example",
                    "version" to "1.0.0",
                    "authors" to "pnFolder",
                )
                else -> null
            }
        }

        fun loggingService(): LoggingService {
            val logger = emptyProxy(PnLogger::class.java)
            val box = NoopMessageBox()
            return proxy(LoggingService::class.java) { methodName ->
                when (methodName) {
                    "logger" -> logger
                    "box", "shutdownBox" -> box
                    else -> null
                }
            }
        }

        fun <T> emptyProxy(type: Class<T>): T = proxy(type) { null }

        fun <T> proxy(type: Class<T>, handler: (String) -> Any?): T {
            val value = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
                handler(method.name) ?: when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Void.TYPE -> Unit
                    else -> null
                }
            }
            return type.cast(value)
        }
    }

    private class NoopMessageBox : MessageBox {
        override fun ok(label: String, detail: String) = this
        override fun warn(label: String, detail: String) = this
        override fun skip(label: String, detail: String) = this
        override fun fail(label: String, detail: String, error: Throwable?) = this
        override fun show() = Unit
    }
}
