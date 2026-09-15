package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.config.ConfigScope
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.events.EventService
import ru.privatenull.pnlibrary.api.events.Listener
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.logging.MessageBox
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.plugin.MetricsController
import ru.privatenull.pnlibrary.api.plugin.PluginBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginContext
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.plugin.PluginLifecycle
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.plugin.PluginMetadataBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginMessages
import ru.privatenull.pnlibrary.api.plugin.PluginOptions
import ru.privatenull.pnlibrary.api.plugin.PluginRegistry
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskService
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.updates.UpdateService
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl
import ru.privatenull.pnlibrary.core.config.ConfigurationServiceImpl
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.text.ComponentService
import ru.privatenull.pnlibrary.core.placeholders.PlaceholderHub
import ru.privatenull.pnlibrary.core.text.ComponentCache
import ru.privatenull.pnlibrary.core.text.ComponentServiceImpl
import ru.privatenull.pnlibrary.api.cooldowns.CooldownService
import ru.privatenull.pnlibrary.api.actions.ActionService
import ru.privatenull.pnlibrary.api.actions.ActionContext
import ru.privatenull.pnlibrary.api.actions.LibraryAudience
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.api.text.ComponentSerializerType
import ru.privatenull.pnlibrary.core.cooldowns.CooldownServiceImpl
import ru.privatenull.pnlibrary.api.currency.CurrencyService
import ru.privatenull.pnlibrary.core.currency.CurrencyHub
import ru.privatenull.pnlibrary.api.currency.CurrencyStorageFactory
import ru.privatenull.pnlibrary.core.currency.CurrencyStorageFactoryImpl

/**
 * Owns plugin-scoped library services and coordinates their lifecycle.
 *
 * Registration behaves transactionally: a context becomes visible only after
 * all requested services have been created. If any step fails, previously
 * created resources are closed in reverse dependency order.
 */
internal class PluginRegistryImpl(
    private val platform: PlatformAdapter,
    private val events: EventService,
    private val tasks: TaskService,
    private val services: ServiceManagerImpl,
    private val logging: LoggingService,
    private val metrics: MetricsService,
    private val diagnostics: DiagnosticsService,
    private val updates: UpdateService,
    private val placeholderHub: PlaceholderHub,
    private val currencyHub: CurrencyHub,
    private val currencyStorageFactory: CurrencyStorageFactory = CurrencyStorageFactoryImpl(),
    private val configurations: ConfigurationServiceImpl = ConfigurationServiceImpl(platform),
) : PluginRegistry {

    private val contexts = linkedMapOf<PluginId, Context>()
    private val owners = IdentityHashMap<Any, Context>()
    private val closed = AtomicBoolean(false)
    private val sharedComponentCache = ComponentCache()
    override fun register(owner: Any, configure: Consumer<PluginBuilder>): PluginContext {
        val details = platform.ownerDetails(owner)
        val nativeId = details["id"] ?: details["name"]
            ?: error("The platform did not expose a plugin ID for ${owner.javaClass.name}")
        return register(owner, PluginId.of(nativeId), configure)
    }

    override fun register(owner: Any, id: PluginId, configure: Consumer<PluginBuilder>): PluginContext =
        synchronized(contexts) {
            check(!closed.get()) { "PluginRegistry is closed" }
            require(id !in contexts) { "Plugin $id is already registered" }
            require(!owners.containsKey(owner)) { "This platform plugin is already registered as ${owners[owner]?.id}" }
            val definition = Builder().also { configure.accept(it) }
            createContext(owner, id, definition).also {
                contexts[id] = it
                owners[owner] = it
            }
        }

    override fun get(id: PluginId): PluginContext? = synchronized(contexts) { contexts[id] }

    override fun unregister(id: PluginId) {
        synchronized(contexts) {
            val context = contexts[id] ?: return
            unregisterLocked(context)
        }
    }

    override fun unregisterOwner(owner: Any) {
        synchronized(contexts) {
            val context = owners[owner] ?: return
            unregisterLocked(context)
        }
    }

    override fun registrations(): List<PluginContext> =
        synchronized(contexts) { contexts.values.toList() }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(contexts) {
            contexts.values.toList().also {
                contexts.clear()
                owners.clear()
            }
        }
        current.forEach { it.closeInternal() }
    }

    private fun createContext(owner: Any, id: PluginId, definition: Builder): Context {
        val metadata = metadata(owner, id, definition)
        var taskScope: TaskScope? = null
        var eventScope: EventScope? = null
        var configScope: ConfigScope? = null
        var metricsController: MetricsControllerImpl? = null
        var diagnosticRegistration: DiagnosticRegistration? = null
        var updateRegistration: UpdateRegistration? = null
        var placeholderScope: PlaceholderService? = null
        var currencyScope: CurrencyService? = null
        try {
            taskScope = tasks.scope(owner)
            eventScope = events.scope(id)
            configScope = configurations.scope(owner)
            placeholderScope = placeholderHub.scope(id, definition.placeholderApiEnabled)
            currencyScope = currencyHub.scope(id, placeholderScope)
            definition.listeners.forEach { eventScope.register(it) }
            metricsController = MetricsControllerImpl(
                owner,
                metrics,
                definition.metricsProjectId,
                definition.metricsEnabled,
                definition.metricsConfigurers,
            )
            definition.diagnosticContainer?.let { container ->
                diagnosticRegistration = diagnostics.register(
                    id.value,
                    requireNotNull(definition.diagnosticsDirectory),
                    container,
                )
            }
            definition.updateRequest?.let { updateRegistration = updates.register(owner, it) }
            return Context(
                owner,
                id,
                metadata,
                taskScope,
                eventScope,
                services.ownedBy(id),
                logging.logger(owner, id.value),
                configScope,
                placeholderScope,
                ComponentServiceImpl(placeholderScope, sharedComponentCache),
                CooldownServiceImpl(),
                currencyScope,
                currencyStorageFactory,
                metricsController,
                diagnosticRegistration,
                updateRegistration,
                definition.placeholderApiEnabled,
                definition.listeners.size,
            )
        } catch (error: Throwable) {
            ResourceCleanup.suppressInto(
                error,
                { updateRegistration?.close() },
                { diagnosticRegistration?.close() },
                { metricsController?.close() },
                { currencyScope?.close() },
                { placeholderScope?.close() },
                { configScope?.close() },
                { eventScope?.close() },
                { services.unregisterAll(id) },
                { taskScope?.close() },
            )
            throw error
        }
    }

    private fun metadata(owner: Any, id: PluginId, definition: Builder): PluginMetadata {
        val details = platform.ownerDetails(owner)
        return PluginMetadata(
            id = id,
            name = definition.metadataName ?: details["name"]?.takeIf { it.isNotBlank() } ?: id.value,
            version = definition.metadataVersion ?: details["version"]?.takeIf { it.isNotBlank() } ?: "unknown",
            authors = definition.metadataAuthors ?: details["authors"]?.takeIf { it.isNotBlank() } ?: "unknown",
            platform = platform.type,
            platformImplementation = platform.implementationName,
            javaVersion = System.getProperty("java.version", "unknown"),
            javaFeature = Runtime.version().feature(),
        )
    }

    private fun unregisterLocked(context: Context) {
        context.closeInternal()
        contexts.remove(context.id, context)
        owners.remove(context.owner)
    }

    private inner class Context(
        val owner: Any,
        override val id: PluginId,
        override val metadata: PluginMetadata,
        override val tasks: TaskScope,
        override val events: EventScope,
        override val services: ServiceManagerImpl.OwnedServices,
        override val logger: PnLogger,
        override val configs: ConfigScope,
        override val placeholders: PlaceholderService,
        override val components: ComponentService,
        override val cooldowns: CooldownService,
        override val currencies: CurrencyService,
        override val currencyStorages: CurrencyStorageFactory,
        override val metrics: MetricsController,
        override val diagnostics: DiagnosticRegistration?,
        override val updates: UpdateRegistration?,
        private val placeholderApiEnabled: Boolean,
        private val listenerCount: Int,
    ) : PluginContext {
        private val contextClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = contextClosed.get()

        override val lifecycle: PluginLifecycle = object : PluginLifecycle {
            override val metadata: PluginMetadata get() = this@Context.metadata
            override fun enabled(): MessageBox =
                logging.box(owner, metadata.name, metadata.version)
                    .ok("Identifier", id.value)
                    .ok("Platform", platformSummary())
                    .status("Metrics", metricsStatus())
                    .status("Updates", updatesStatus())
                    .status("Diagnostics", if (diagnostics == null) null else "enabled")
                    .status("PlaceholderAPI", placeholderApiStatus())
                    .status("Events", if (listenerCount == 0) null else "$listenerCount listener(s)")
            override fun disabled(): MessageBox =
                logging.shutdownBox(owner, metadata.name, metadata.version)
                    .status("Resources", if (isClosed) "released" else "close pending")
                    .status("Updates", if (updates == null) null else if (isClosed) "stopped" else "registered")
                    .status("Metrics", if (metrics.projectId == null) null else if (isClosed) "stopped" else metricsStatus())
                    .status("Events", if (listenerCount == 0) null else if (isClosed) "$listenerCount listener(s) removed" else "$listenerCount listener(s)")
        }
        override val messages: PluginMessages = object : PluginMessages {
            override fun box(title: String): MessageBox {
                require(title.isNotBlank()) { "message box title must not be blank" }
                return logging.box(owner, title.trim())
            }
        }
        override val actions: ActionService = object : ActionService {
            override fun context(
                player: LibraryPlayer,
                allPlayers: LibraryAudience,
                values: Map<String, Any?>,
                serializerType: ComponentSerializerType,
            ) = ActionContext(player, allPlayers, components, logger, tasks, serializerType, values)
        }

        override fun close() {
            synchronized(contexts) {
                unregisterLocked(this)
            }
        }

        fun closeInternal() {
            if (!contextClosed.compareAndSet(false, true)) return
            ResourceCleanup.closeAll(
                { updates?.close() },
                { diagnostics?.close() },
                { this@PluginRegistryImpl.diagnostics.clearPlugin(id.value) },
                { metrics.close() },
                { currencies.close() },
                { cooldowns.close() },
                { placeholders.close() },
                { configs.close() },
                { services.close() },
                { events.close() },
                { tasks.close() },
            )
        }

        private fun platformSummary(): String =
            if (metadata.platformImplementation.equals(metadata.platform.displayName, ignoreCase = true)) {
                metadata.platform.displayName
            } else {
                "${metadata.platform.displayName} · ${metadata.platformImplementation}"
            }

        private fun metricsStatus(): String? = metrics.projectId?.let { projectId ->
            "${if (metrics.isEnabled) "enabled" else "disabled"} · project $projectId"
        }

        private fun updatesStatus(): String? = updates?.let {
            "${it.snapshot.channel.name} · ${it.repository} · Java ${it.snapshot.requiredJava}+"
        }

        private fun placeholderApiStatus(): String = if (!placeholderApiEnabled) {
            "disabled"
        } else {
            placeholderHub.get("placeholderapi")?.state?.name?.lowercase() ?: "unavailable"
        }

        private fun MessageBox.status(label: String, detail: String?): MessageBox =
            if (detail == null) skip(label, "not configured") else ok(label, detail)

    }

    private class Builder : PluginBuilder {
        var metadataName: String? = null
        var metadataVersion: String? = null
        var metadataAuthors: String? = null
        var metricsProjectId: Int? = null
        var metricsEnabled: Boolean = false
        val metricsConfigurers = mutableListOf<Consumer<PluginMetrics>>()
        var diagnosticsDirectory: Path? = null
        var diagnosticContainer: DiagnosticContainer? = null
        var updateRequest: PluginUpdateRequest? = null
        var placeholderApiEnabled: Boolean = true
        val listeners = mutableListOf<Listener>()

        override fun metadata(configure: Consumer<PluginMetadataBuilder>): PluginBuilder = apply {
            configure.accept(MetadataBuilder(this))
        }

        override fun options(configure: Consumer<PluginOptions>): PluginBuilder = apply {
            configure.accept(OptionsBuilder(this))
        }

        override fun metrics(
            projectId: Int,
            enabled: Boolean,
            configure: Consumer<PluginMetrics>,
        ): PluginBuilder = apply {
            require(projectId > 0) { "metrics projectId must be positive" }
            metricsProjectId = projectId
            metricsEnabled = enabled
            metricsConfigurers += configure
        }

        override fun diagnostics(dataDirectory: Path, container: DiagnosticContainer): PluginBuilder = apply {
            diagnosticsDirectory = dataDirectory
            diagnosticContainer = container
        }

        override fun updates(request: PluginUpdateRequest): PluginBuilder = apply {
            updateRequest = request
        }

        override fun listener(listener: Listener): PluginBuilder = apply {
            listeners += listener
        }
    }

    private class OptionsBuilder(private val target: Builder) : PluginOptions {
        override fun placeholderApi(enabled: Boolean): PluginOptions = apply {
            target.placeholderApiEnabled = enabled
        }
    }

    private class MetadataBuilder(private val target: Builder) : PluginMetadataBuilder {
        override fun name(value: String): PluginMetadataBuilder = apply {
            target.metadataName = value.requireMetadata("name")
        }
        override fun version(value: String): PluginMetadataBuilder = apply {
            target.metadataVersion = value.requireMetadata("version")
        }
        override fun authors(value: String): PluginMetadataBuilder = apply {
            target.metadataAuthors = value.requireMetadata("authors")
        }

        private fun String.requireMetadata(field: String): String =
            trim().also { require(it.isNotEmpty()) { "plugin metadata $field must not be blank" } }
    }

}
