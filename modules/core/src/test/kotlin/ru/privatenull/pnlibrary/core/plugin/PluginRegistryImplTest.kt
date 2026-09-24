package ru.privatenull.pnlibrary.core.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.commands.CommandService
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
import ru.privatenull.pnlibrary.api.plugin.PluginBuilder
import ru.privatenull.pnlibrary.api.plugin.Dependencies
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskService
import ru.privatenull.pnlibrary.api.updates.UpdateService
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.downloads.PluginDownloads
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import ru.privatenull.pnlibrary.currency.CurrencyFeature
import ru.privatenull.pnlibrary.core.events.EventServiceImpl
import ru.privatenull.pnlibrary.core.placeholders.PlaceholderHub
import ru.privatenull.pnlibrary.core.testing.TestTaskService
import ru.privatenull.pnlibrary.core.tasks.TaskServiceImpl
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl
import java.lang.reflect.Proxy
import java.util.function.Supplier
import java.util.function.Consumer

class PluginRegistryImplTest {
    @Test
    fun `depends creates an implicit component descriptor when no update declaration exists`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            registry(libraryVersion = "1.0.0").registerModule(Any(), "example") {
                it.depends(Dependencies.managed("missing-component", "9.0.0", "pnFolder", "Missing"))
            }
        }
        assertTrue(error.message!!.contains("missing-component >= 9.0.0"))
    }

    @Test
    fun `optional unified dependency does not block registration`() {
        val context = registry(libraryVersion = "1.0.0").registerModule(Any(), "example") {
            it.depends(Dependencies.managed("missing-component", "9.0.0", "pnFolder", "Missing", required = false))
            .product(ProductDescriptor.builder("example", "1.0.0").pnLibraryApi(1, 1).build())
        }
        assertEquals("example", context.id.value)
        context.close()
    }

    @Test
    fun `updates declaration infers component without separate descriptor`() {
        val request = PluginUpdateRequest.builder().repository("pnFolder", "Example")
            .apiVersions(1, 2)
            .artifact("Example.jar", PlatformType.BUKKIT, 17).build()

        val error = assertThrows(IllegalArgumentException::class.java) {
            registry().registerModule(Any(), "example") {
                it.updates(request)
                    .depends(Dependencies.managed("economy", "2.0.0", "pnFolder", "Economy"))
            }
        }

        assertTrue(error.message!!.contains("economy >= 2.0.0"))
    }

    @Test
    fun `declared component delivery satisfies missing managed dependency gate`() {
        val request = PluginUpdateRequest.builder().repository("pnFolder", "Example")
            .apiVersion(1)
            .artifact("Example.jar", 17).build()
        val downloads = PluginDownloads.builder().component("economy") {
            it.version("2.0.0").apiVersion(1).url("https://example.org/Economy.jar")
        }.build()

        val context = registry().registerModule(Any(), "example") {
            it.updates(request)
                .depends(Dependencies.managed("economy", "2.0.0", "pnFolder", "Economy"))
                .downloads(downloads)
        }

        assertEquals("example", context.id.value)
    }

    @Test
    fun `updates registration inherits component metadata`() {
        val updates = RecordingUpdateService()
        val descriptor = ProductDescriptor.builder("example", "1.0.0")
            .pnLibraryApi(1, 2)
            .build()
        val request = PluginUpdateRequest.builder()
            .repository("pnFolder", "Example")
            .artifact("(?i)^Example-.*\\.jar$", 17)
            .build()

        registry(updates = updates, libraryVersion = "1.5.0").registerModule(Any(), "example") {
            it.product(descriptor)
                .depends(Dependencies.managed("pnlibrary", "1.5.0", "pnFolder", "pnLibrary"))
                .updates(request)
        }

        assertEquals("example", updates.product!!.id.value)
        assertEquals(1, updates.request!!.supportedApi.minimum)
        assertEquals(2, updates.request!!.supportedApi.maximum)
        assertEquals("pnlibrary", updates.dependencies.single().managed!!.product.value)
        assertEquals("1.5.0", updates.dependencies.single().versions.minimum.toString())
    }

    @Test
    fun `dependency gate runs before plugin scopes become visible`() {
        val owner = Any()
        val taskScope = RecordingTaskScope(owner)
        val registry = registry(tasks = RecordingTaskService(taskScope), libraryVersion = "1.0.0")
        val descriptor = ProductDescriptor.builder("example", "1.0.0")
            .pnLibraryApi(1, 1)
            .build()

        val error = assertThrows(IllegalArgumentException::class.java) {
            registry.registerModule(owner, "example") {
                it.product(descriptor)
                    .depends(Dependencies.managed("economy", "2.0.0", "pnFolder", "Economy"))
            }
        }

        assertTrue(error.message!!.contains("economy >= 2.0.0"))
        assertFalse(taskScope.closed)
        assertTrue(registry.registrations().single().modules().isEmpty())
    }

    @Test
    fun `registered compatible component satisfies a later dependency`() {
        val registry = registry(libraryVersion = "1.0.0")
        registry.registerModule(Any(), "economy") {
            it.product(ProductDescriptor.builder("economy", "2.1.0").pnLibraryApi(1, 1).build())
        }

        val dependent = registry.registerModule(Any(), "example") {
            it.product(ProductDescriptor.builder("example", "1.0.0").pnLibraryApi(1, 1).build())
                .depends(Dependencies.managed("economy", "2.0.0", "pnFolder", "Economy"))
        }

        assertEquals("example", dependent.id.value)
        registry.close()
    }

    @Test
    fun `external dependency diagnostic includes its download page`() {
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.3")
            .downloadPage("https://github.com/MilkBowl/Vault/releases").build()
        val descriptor = ProductDescriptor.builder("example", "1.0.0").pnLibraryApi(1, 1).build()

        val error = assertThrows(IllegalArgumentException::class.java) {
            registry().registerModule(Any(), "example") { it.product(descriptor).depends(dependency) }
        }

        assertTrue(error.message!!.contains("Vault >= 1.7.3"))
        assertTrue(error.message!!.contains("https://github.com/MilkBowl/Vault/releases"))
    }

    @Test
    fun `closing physical plugin context unregisters commands owned by its native plugin`() {
        val owner = Any()
        val commands = RecordingCommandService()
        val registry = registry(commands = commands)
        val plugin = registry.register(owner)
        val context = plugin.registerModule("example") { }

        plugin.close()

        assertEquals(listOf(owner), commands.unregisteredOwners)
    }

    @Test
    fun `context is globally addressable by normalized plugin ID`() {
        val owner = Any()
        val taskScope = RecordingTaskScope(owner)
        val tasks = RecordingTaskService(taskScope)
        val events = EventServiceImpl(TestTaskService()) { _, _, _ -> }
        val metrics = RecordingMetricsService()
        val registry = registry(events = events, tasks = tasks, metrics = metrics)
        val listener = RecordingListener()

        val plugin = registry.register(owner)
        val context = plugin.registerModule("pnClans") {
            it.listener(listener).metrics(42, false)
        }

        assertSame(context, plugin.getModule("PNCLANS"))
        assertEquals("pnclans", context.id.value)
        assertFalse(context.metrics.isEnabled)
        context.metrics.enable()
        assertTrue(context.metrics.isEnabled)
        assertEquals(42, metrics.lastProjectId)
        assertEquals(1, events.publish(TestEvent()).join().delivered)

        context.close()

        assertNull(plugin.getModule("pnclans"))
        assertTrue(context.isClosed)
        assertEquals(0, events.publish(TestEvent()).join().delivered)
        assertTrue(taskScope.closed)
    }

    @Test
    fun `duplicate module ID is rejected within one plugin`() {
        val owner = Any()
        val taskScope = RecordingTaskScope(owner)
        val tasks = RecordingTaskService(taskScope)
        val registry = registry(tasks = tasks)
        val plugin = registry.register(owner)
        plugin.registerModule("example") { }

        assertThrows(IllegalArgumentException::class.java) {
            plugin.registerModule("EXAMPLE") { }
        }
        registry.close()
    }

    @Test
    fun `one platform owner can be registered under multiple module IDs`() {
        val owner = Any()
        val registry = registry(tasks = RecordingTaskService(RecordingTaskScope(owner)))
        val plugin = registry.register(owner)
        val first = plugin.registerModule("first") { }
        val second = plugin.registerModule("second") { }

        assertEquals("first", first.id.value)
        assertEquals("second", second.id.value)
        assertEquals(1, registry.registrations().size)
        assertEquals(2, plugin.modules().size)
        registry.close()
    }

    @Test
    fun `same physical owner cannot be registered twice`() {
        val owner = Any()
        val registry = registry()
        registry.register(owner)

        assertThrows(IllegalArgumentException::class.java) {
            registry.register(owner)
        }
    }

    @Test
    fun `different owners may use the same local module ID`() {
        val firstOwner = Any()
        val secondOwner = Any()
        val registry = registry()

        val first = registry.register(firstOwner).registerModule("core") { }
        val second = registry.register(secondOwner).registerModule("core") { }

        assertEquals("core", first.id.value)
        assertEquals("core", second.id.value)
        assertEquals(2, registry.registrations().size)
    }

    @Test
    fun `component identity remains unique across different plugin contexts`() {
        val registry = registry()
        val descriptor = ProductDescriptor.builder("shared-component", "1.0.0")
            .pnLibraryApi(1, 1).build()
        registry.register(Any()).registerModule("shared-component") { it.product(descriptor) }

        assertThrows(IllegalArgumentException::class.java) {
        registry.register(Any()).registerModule("shared-component") { it.product(descriptor) }
        }
    }

    @Test
    fun `maximum length owner and module IDs produce a valid service namespace`() {
        val owner = Any()
        val longId = "a".repeat(64)
        val platform = proxy(PlatformAdapter::class.java) { methodName ->
            when (methodName) {
                "getType" -> PlatformType.BUKKIT
                "getImplementationName" -> "Paper"
                "acceptsOwner" -> true
                "ownerDetails" -> mapOf("id" to longId, "name" to longId, "version" to "1.0.0")
                "installedPlugins" -> emptyMap<String, String>()
                else -> null
            }
        }
        val registry = registry(platform = platform)

        val module = registry.register(owner).registerModule("b".repeat(64)) { }

        assertEquals("b".repeat(64), module.id.value)
    }

    @Test
    fun `closing one module keeps its sibling live`() {
        val registry = registry()
        val plugin = registry.register(Any())
        val first = plugin.registerModule("first") { }
        val second = plugin.registerModule("second") { }

        first.close()

        assertTrue(first.isClosed)
        assertFalse(second.isClosed)
        assertNull(plugin.getModule("first"))
        assertSame(second, plugin.getModule("second"))
    }

    @Test
    fun `sibling modules receive isolated configuration scopes`() {
        val plugin = registry().register(Any())
        val first = plugin.registerModule("first") { }
        val second = plugin.registerModule("second") { }

        assertNotSame(first.configs, second.configs)
        first.close()
        assertEquals(0, second.configs.size)
    }

    @Test
    fun `sibling modules receive isolated task scopes`() {
        val tasks = TaskServiceImpl(emptyProxy(PlatformTaskAdapter::class.java))
        val plugin = registry(tasks = tasks).register(Any())
        val first = plugin.registerModule("first") { }
        val second = plugin.registerModule("second") { }

        assertNotSame(first.tasks, second.tasks)
    }

    @Test
    fun `plugin context registers and closes multiple logical modules`() {
        val owner = Any()
        val registry = registry()
        val modules = registry.register(owner)

        val core = modules.registerModule("example-core") { }
        val economy = modules.registerModule("example-economy") { }

        assertEquals(setOf("example-core", "example-economy"), modules.modules().map { it.id.value }.toSet())
        modules.close()
        assertTrue(core.isClosed)
        assertTrue(economy.isClosed)
        assertTrue(registry.registrations().isEmpty())
    }

    @Test
    fun `unsupported owner is rejected before module context is created`() {
        val owner = Any()
        val platform = proxy(PlatformAdapter::class.java) { methodName ->
            when (methodName) {
                "getType" -> PlatformType.BUKKIT
                "getImplementationName" -> "Paper"
                "acceptsOwner" -> false
                else -> null
            }
        }

        val registry = registry(platform = platform)
        val error = assertThrows(IllegalArgumentException::class.java) {
            registry.register(owner)
        }

        assertTrue(error.message!!.contains("not a supported owner"))
        assertTrue(registry.registrations().isEmpty())
    }

    @Test
    fun `platform owner cleanup removes its global context`() {
        val owner = Any()
        val taskScope = RecordingTaskScope(owner)
        val registry = registry(tasks = RecordingTaskService(taskScope))
        val plugin = registry.register(owner)
        val context = plugin.registerModule("example") { }

        registry.unregister(owner)

        assertTrue(context.isClosed)
        assertNull(registry.get(owner))
        assertTrue(taskScope.closed)
    }

    @Test
    fun `native registration resolves metadata and lifecycle messages are explicit`() {
        val owner = Any()
        val taskScope = RecordingTaskScope(owner)
        val logging = RecordingLoggingService()
        val registry = registry(tasks = RecordingTaskService(taskScope), logging = logging)

        val context = registry.register(owner).registerModule("example") { plugin ->
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
        fun PluginRegistryImpl.registerModule(
            owner: Any,
            id: String,
            configure: Consumer<PluginBuilder>,
        ) = (get(owner) ?: register(owner)).registerModule(id, configure)

        fun registry(
            platform: PlatformAdapter = platform(),
            events: EventServiceImpl = EventServiceImpl(TestTaskService()) { _, _, _ -> },
            tasks: TaskService = RecordingTaskService(RecordingTaskScope(Any())),
            logging: LoggingService = loggingService(),
            metrics: MetricsService = RecordingMetricsService(),
            commands: CommandService? = null,
            libraryVersion: String? = null,
            updates: UpdateService = emptyProxy(UpdateService::class.java),
        ): PluginRegistryImpl =
            PluginRegistryImpl(
                platform = platform,
                events = events,
                tasks = tasks,
                services = ServiceManagerImpl(),
                logging = logging,
                metrics = metrics,
                diagnostics = emptyProxy(DiagnosticsService::class.java),
                updates = updates,
                placeholderHub = PlaceholderHub(platform),
                currencyFeature = CurrencyFeature(),
                commands = commands,
                libraryVersion = libraryVersion,
            )

        private class RecordingUpdateService : UpdateService {
            var request: PluginUpdateRequest? = null
            var product: ProductDescriptor? = null
            var dependencies: List<ru.privatenull.pnlibrary.api.plugin.PluginDependency> = emptyList()
            override fun register(
                owner: Any,
                product: ProductDescriptor,
                request: PluginUpdateRequest,
                dependencies: List<ru.privatenull.pnlibrary.api.plugin.PluginDependency>,
            ): UpdateRegistration {
                this.product = product
                this.request = request
                this.dependencies = dependencies
                return emptyProxy(UpdateRegistration::class.java)
            }
            override fun registrations(): List<UpdateRegistration> = emptyList()
            override fun find(product: String): UpdateRegistration? = null
        }

        fun platform(): PlatformAdapter = proxy(PlatformAdapter::class.java) { methodName ->
            when (methodName) {
                "getType" -> PlatformType.BUKKIT
                "getImplementationName" -> "Paper"
                "acceptsOwner" -> true
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

    private class RecordingCommandService : CommandService {
        val unregisteredOwners = mutableListOf<Any>()
        override fun register(owner: Any, command: CommandDefinition): CommandRegistration =
            throw UnsupportedOperationException()
        override fun unregisterOwner(owner: Any) {
            unregisteredOwners += owner
        }
    }
}
