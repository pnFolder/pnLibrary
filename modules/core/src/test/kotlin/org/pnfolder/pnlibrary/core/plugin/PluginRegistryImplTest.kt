package org.pnfolder.pnlibrary.core.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.dependencies.Dependencies
import org.pnfolder.pnlibrary.api.dependencies.ExternalPluginDependency
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService
import org.pnfolder.pnlibrary.api.downloads.DownloadDestination
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.downloads.FileDownloads
import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventHandler
import org.pnfolder.pnlibrary.api.events.Listener
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.logging.MessageBox
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.plugin.LifecycleRow
import org.pnfolder.pnlibrary.api.plugin.PluginBuilder
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.api.updates.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.core.commands.CommandService
import org.pnfolder.pnlibrary.core.downloads.DirectDownloadManager
import org.pnfolder.pnlibrary.core.events.EventServiceImpl
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderHub
import org.pnfolder.pnlibrary.core.services.ServiceManagerImpl
import org.pnfolder.pnlibrary.core.tasks.TaskService
import org.pnfolder.pnlibrary.core.tasks.TaskServiceImpl
import org.pnfolder.pnlibrary.currency.CurrencyFeature
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskRequest
import java.lang.reflect.Proxy
import java.nio.file.Paths
import java.util.function.Consumer
import java.util.function.Supplier

class PluginRegistryImplTest {
    @Test
    fun `failed file registration rolls back previously registered dependency downloads`() {
        val owner = Any()
        val downloads = mock(DirectDownloadManager::class.java)
        val dependencyRegistration = mock(DownloadRegistration::class.java)
        val failure = IllegalStateException("file registration failed")
        val request = FileDownloads.builder().dataDirectory(Paths.get("plugins/example"))
            .file("asset") {
                it.url("https://example.org/asset.bin").destination(DownloadDestination.DATA_FOLDER, "asset.bin")
            }.build()
        `when`(downloads.registerDependencies(owner, emptyList())).thenReturn(dependencyRegistration)
        `when`(downloads.register(owner, request)).thenThrow(failure)
        val registry = registry(directDownloads = downloads)
        val thrown = assertThrows(IllegalStateException::class.java) {
            registry.register(owner).registerModule("example") { it.downloads(request) }
        }
        assertSame(failure, thrown)
        verify(dependencyRegistration).close()
        registry.close()
    }

    @Test
    fun `registry shutdown closes remaining plugins and downloads after a plugin fails`() {
        val firstOwner = Any()
        val secondOwner = Any()
        val commands = mock(CommandService::class.java)
        val downloads = mock(DirectDownloadManager::class.java)
        val failure = IllegalStateException("plugin cleanup failed")
        doThrow(failure).`when`(commands).unregisterOwner(firstOwner)
        val registry = registry(commands = commands, directDownloads = downloads)
        registry.register(firstOwner)
        registry.register(secondOwner)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { registry.close() })
        verify(commands).unregisterOwner(firstOwner)
        verify(commands).unregisterOwner(secondOwner)
        verify(downloads).close()
        assertTrue(registry.all().isEmpty())
    }

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
            .product(ProductDescriptor.builder().version("1.0.0").pnLibraryApi(1, 1).build())
        }
        assertEquals("example", context.id.value)
        context.close()
    }

    @Test
    fun `updates declaration infers component without separate descriptor`() {
        val request = PluginUpdateRequest.builder().repository("pnFolder", "Example")
            .supportedApi(1, 2)
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
    fun `ordinary file delivery does not satisfy missing managed dependency gate`() {
        val request = PluginUpdateRequest.builder().repository("pnFolder", "Example")
            .supportedApi(1, 1)
            .artifact("Example.jar", 17).build()
        val downloads = FileDownloads.builder().dataDirectory(Paths.get("plugins/example"))
            .file("economy-data") {
                it.url("https://example.org/data.bin")
                    .destination(DownloadDestination.DATA_FOLDER, "data.bin")
            }.build()

        assertThrows(IllegalArgumentException::class.java) {
            registry().registerModule(Any(), "example") {
                it.updates(request)
                    .depends(Dependencies.managed("economy", "2.0.0", "pnFolder", "Economy"))
                    .downloads(downloads)
            }
        }
    }

    @Test
    fun `updates registration inherits component metadata`() {
        val updates = RecordingUpdateService()
        val descriptor = ProductDescriptor.builder().version("1.0.0")
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
        val descriptor = ProductDescriptor.builder().version("1.0.0")
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
        assertTrue(registry.all().single().all().isEmpty())
    }

    @Test
    fun `registered compatible component satisfies a later dependency`() {
        val registry = registry(libraryVersion = "1.0.0")
        registry.registerModule(Any(), "economy") {
            it.product(ProductDescriptor.builder().version("2.1.0").pnLibraryApi(1, 1).build())
        }

        val dependent = registry.registerModule(Any(), "example") {
            it.product(ProductDescriptor.builder().version("1.0.0").pnLibraryApi(1, 1).build())
                .depends(Dependencies.managed("economy", "2.0.0", "pnFolder", "Economy"))
        }

        assertEquals("example", dependent.id.value)
        registry.close()
    }

    @Test
    fun `external dependency diagnostic includes its download page`() {
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.3")
            .downloadPage("https://github.com/MilkBowl/Vault/releases").build()
        val descriptor = ProductDescriptor.builder().version("1.0.0").pnLibraryApi(1, 1).build()

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
        val events = EventServiceImpl { _, _, _ -> }
        val metrics = RecordingMetricsService()
        val registry = registry(events = events, tasks = tasks, metrics = metrics)
        val listener = RecordingListener()

        val plugin = registry.register(owner)
        val context = plugin.registerModule("pnClans") {
            it.metrics { metrics ->
                metrics.bStats(42).enabled(false)
            }
        }
        context.events.register(listener)

        assertSame(context, plugin.getModule("PNCLANS"))
        assertEquals("pnclans", context.id.value)
        assertFalse(context.metrics.isEnabled)
        context.metrics.enable()
        assertTrue(context.metrics.isEnabled)
        assertEquals(42, metrics.lastProjectId)
        events.callEvent(TestEvent())
        assertEquals(1, listener.calls)

        context.close()

        assertNull(plugin.getModule("pnclans"))
        assertTrue(context.isClosed)
        events.callEvent(TestEvent())
        assertEquals(1, listener.calls)
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
        assertEquals(1, registry.all().size)
        assertEquals(2, plugin.all().size)
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
        assertEquals(2, registry.all().size)
    }

    @Test
    fun `component identity remains unique across different plugin contexts`() {
        val registry = registry()
        val descriptor = ProductDescriptor.builder().version("1.0.0")
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
        val nativeOwners = mutableListOf<Any?>()
        val native = object : PlatformTaskAdapter {
            override fun schedule(request: PlatformTaskRequest): PlatformTaskHandle {
                nativeOwners += request.owner
                return PlatformTaskHandle { true }
            }
        }
        val tasks = TaskServiceImpl(native)
        val owner = Any()
        val plugin = registry(tasks = tasks).register(owner)
        val first = plugin.registerModule("first") { }
        val second = plugin.registerModule("second") { }

        assertNotSame(first.tasks, second.tasks)
        val spec = TaskSpec.builder().action { }.build()
        val firstTask = first.tasks.schedule(spec)
        val secondTask = second.tasks.schedule(spec)
        assertSame(owner, nativeOwners[0])
        assertSame(owner, nativeOwners[1])
        first.close()
        assertTrue(firstTask.isCancelled)
        assertFalse(secondTask.isCancelled)
        assertThrows(IllegalStateException::class.java) { first.tasks.schedule(spec) }
        plugin.close()
        assertTrue(secondTask.isCancelled)
        tasks.close()
    }

    @Test
    fun `module commands use native plugin and close independently`() {
        val owner = Any()
        val commands = RecordingCommandService()
        val plugin = registry(commands = commands).register(owner)
        val first = plugin.registerModule("first") { }
        val second = plugin.registerModule("second") { }
        val firstCommand = first.registerCommand("first") { }
        val secondCommand = second.registerCommand("second") { }

        assertSame(owner, commands.registeredOwners[0])
        assertSame(owner, commands.registeredOwners[1])
        first.close()
        assertTrue(firstCommand.isClosed)
        assertFalse(secondCommand.isClosed)
        assertThrows(IllegalStateException::class.java) { first.registerCommand("late") { } }
        plugin.close()
        assertTrue(secondCommand.isClosed)
    }

    @Test
    fun `plugin context registers and closes multiple logical modules`() {
        val owner = Any()
        val registry = registry()
        val modules = registry.register(owner)

        val core = modules.registerModule("example-core") { }
        val economy = modules.registerModule("example-economy") { }

        assertEquals(setOf("example-core", "example-economy"), modules.all().map { it.id.value }.toSet())
        modules.close()
        assertTrue(core.isClosed)
        assertTrue(economy.isClosed)
        assertTrue(registry.all().isEmpty())
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
        assertTrue(registry.all().isEmpty())
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

    @Test
    fun `an enabled card can leave out the rows pnLibrary adds by itself`() {
        val logging = RecordingLoggingService()
        val context = registry(logging = logging).register(Any()).registerModule("example") { }

        context.lifecycle.enabled().ok("Mine", "x").show()
        assertEquals(listOf("Identifier", "Platform", "Metrics", "Updates", "Diagnostics", "PlaceholderAPI", "Mine"), logging.rows)

        logging.rows.clear()
        context.lifecycle.enabled().hide(LifecycleRow.IDENTIFIER, LifecycleRow.PLATFORM).ok("Mine", "x").show()
        assertEquals(listOf("Metrics", "Updates", "Diagnostics", "PlaceholderAPI", "Mine"), logging.rows)

        logging.rows.clear()
        context.lifecycle.enabled().hide(LifecycleRow.NOT_CONFIGURED).ok("Mine", "x").show()
        assertEquals(listOf("Identifier", "Platform", "PlaceholderAPI", "Mine"), logging.rows)
    }

    private class RecordingListener : Listener {
        var calls = 0
            private set

        @EventHandler
        fun handle(event: TestEvent) {
            calls++
        }
    }

    private class TestEvent : Event()

    private class RecordingMetricsService : MetricsService {
        var lastProjectId: Int? = null
        override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
            lastProjectId = configurations.firstNotNullOfOrNull { it.projectId }
            return NoopMetrics(configurations.map { it.provider }.toSet())
        }
    }

    private class NoopMetrics(override val providers: Set<MetricsProvider>) : PluginMetrics {
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
        val rows = mutableListOf<String>()
        override fun logger(owner: Any, name: String): PnLogger = emptyProxy(PnLogger::class.java)
        override fun box(owner: Any, title: String): MessageBox =
            RecordingMessageBox(rows) { genericMessages++ }
        override fun box(owner: Any, name: String, version: String): MessageBox =
            RecordingMessageBox(rows) { startupMessages++ }
        override fun shutdownBox(owner: Any, title: String): MessageBox =
            RecordingMessageBox(rows) { shutdownMessages++ }
    }

    private class RecordingMessageBox(private val rows: MutableList<String>, private val onShow: () -> Unit) : MessageBox {
        override fun ok(label: String, detail: String) = apply { rows += label }
        override fun warn(label: String, detail: String) = apply { rows += label }
        override fun skip(label: String, detail: String) = apply { rows += label }
        override fun fail(label: String, detail: String, error: Throwable?) = apply { rows += label }
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
            events: EventServiceImpl = EventServiceImpl { _, _, _ -> },
            tasks: TaskService = RecordingTaskService(RecordingTaskScope(Any())),
            logging: LoggingService = loggingService(),
            metrics: MetricsService = RecordingMetricsService(),
            commands: CommandService? = null,
            libraryVersion: String? = null,
            updates: UpdateService = emptyProxy(UpdateService::class.java),
            directDownloads: DirectDownloadManager? = null,
        ): PluginRegistryImpl =
            PluginRegistryImpl(
                ownership = platform,
                identity = platform,
                execution = platform,
                platformLogging = platform,
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
                directDownloads = directDownloads,
                libraryVersion = libraryVersion,
            )

        private class RecordingUpdateService : UpdateService {
            var request: PluginUpdateRequest? = null
            var product: ProductDescriptor? = null
            var dependencies: List<PluginDependency> = emptyList()
            override fun register(
                owner: Any,
                product: ProductDescriptor,
                request: PluginUpdateRequest,
                dependencies: List<PluginDependency>,
            ): UpdateRegistration {
                this.product = product
                this.request = request
                this.dependencies = dependencies
                return emptyProxy(UpdateRegistration::class.java)
            }
            override fun all(): List<UpdateRegistration> = emptyList()
            override fun get(product: String): UpdateRegistration? = null
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
        val registeredOwners = mutableListOf<Any>()
        override fun register(owner: Any, command: CommandDefinition,
            feedback: org.pnfolder.pnlibrary.core.commands.CommandFeedbackScope?): CommandRegistration =
            object : CommandRegistration {
                init { registeredOwners += owner }
                override val command = command
                override var isClosed = false
                    private set
                override fun close() { isClosed = true }
            }
        override fun unregisterOwner(owner: Any) {
            unregisteredOwners += owner
        }
    }
}
