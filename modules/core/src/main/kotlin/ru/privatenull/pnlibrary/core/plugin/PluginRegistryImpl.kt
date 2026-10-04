package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.config.ConfigScope
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.events.EventService
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.logging.MessageBox
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.plugin.MetricsController
import ru.privatenull.pnlibrary.api.plugin.PluginBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.plugin.ModuleContext
import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginLifecycle
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.plugin.PluginMessages
import ru.privatenull.pnlibrary.api.plugin.PluginRegistry
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.core.remote.RemotePolicyNoticeRenderer
import ru.privatenull.pnlibrary.core.remote.RemotePolicyMonitor
import ru.privatenull.pnlibrary.api.tasks.TaskService
import ru.privatenull.pnlibrary.core.tasks.TaskServiceImpl
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.updates.UpdateService
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl
import ru.privatenull.pnlibrary.core.config.ConfigurationServiceImpl
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
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
import ru.privatenull.pnlibrary.api.currency.CurrencyStorageFactory
import ru.privatenull.pnlibrary.api.commands.CommandService
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.currency.CurrencyFeature
import ru.privatenull.pnlibrary.core.downloads.DirectDownloadManager
import ru.privatenull.pnlibrary.core.downloads.CompositeDownloadRegistration

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
    private val currencyFeature: CurrencyFeature,
    private val configurations: ConfigurationServiceImpl = ConfigurationServiceImpl(platform),
    private val commands: CommandService? = null,
    private val directDownloads: DirectDownloadManager? = null,
    libraryVersion: String? = null,
) : PluginRegistry {

    private val plugins = IdentityHashMap<Any, Plugin>()
    private val productDescriptors = linkedMapOf<String, ProductDescriptor>().apply {
        libraryVersion?.let(SemanticVersion::tryParse)?.let { put("pnlibrary", ProductDescriptor.library(it.toString())) }
    }
    private val closed = AtomicBoolean(false)
    private val registrationSequence = AtomicLong()
    private val sharedComponentCache = ComponentCache()
    private val productResolver = ModuleProductResolver(platform)
    private val metadataFactory = ModuleMetadataFactory(platform)
    private val remotePolicyNotices = RemotePolicyNoticeRenderer(platform)
    private val remotePolicies = RemotePolicyMonitor(platform, remotePolicyNotices)
    private val dependencyValidator = ModuleDependencyValidator(platform) {
        directDownloads != null
    }
    override fun register(owner: Any): PluginRegistration = synchronized(plugins) {
        check(!closed.get()) { "PluginRegistry is closed" }
        require(platform.acceptsOwner(owner)) {
            "Object ${owner.javaClass.name} is not a supported owner for ${platform.type.id}"
        }
        require(!plugins.containsKey(owner)) { "This platform plugin is already registered" }
        val details = platform.ownerDetails(owner)
        val nativeId = details["id"] ?: details["name"]
            ?: error("The platform did not expose a plugin ID for ${owner.javaClass.name}")
        Plugin(owner, PluginId.of(nativeId), registrationSequence.incrementAndGet()).also { plugins[owner] = it }
    }

    override fun get(owner: Any): PluginRegistration? = synchronized(plugins) { plugins[owner] }

    override fun unregister(owner: Any) {
        detachPlugin(owner)?.closeInternal()
    }

    override fun all(): List<PluginRegistration> =
        synchronized(plugins) { java.util.Collections.unmodifiableList(ArrayList(plugins.values)) }

    @Deprecated("Use all()", ReplaceWith("all()"))
    override fun registrations(): List<PluginRegistration> = all()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(plugins) {
            plugins.values.toList().also {
                plugins.clear()
                productDescriptors.clear()
            }
        }
        current.forEach { it.closeInternal() }
        directDownloads?.close()
    }

    private fun createContext(
        parent: Plugin,
        id: ModuleId,
        serviceKey: PluginId,
        definition: ModuleDefinitionBuilder,
        productDescriptor: ProductDescriptor?,
    ): Context {
        val owner = parent.owner
        val metadata = metadataFactory.create(owner, id, definition)
        var taskScope: TaskScope? = null
        var eventScope: EventScope? = null
        var configScope: ConfigScope? = null
        var metricsController: MetricsControllerImpl? = null
        var diagnosticRegistration: DiagnosticRegistration? = null
        var updateRegistration: UpdateRegistration? = null
        var downloadRegistration: DownloadRegistration? = null
        var placeholderScope: PlaceholderService? = null
        var currencyScope: CurrencyService? = null
        val serviceScope = services.ownedBy(serviceKey)
        try {
            taskScope = if (tasks is TaskServiceImpl) tasks.scope(owner, serviceKey) else tasks.scope(owner)
            eventScope = events.scope(serviceKey)
            configScope = configurations.scope(owner, serviceKey)
            placeholderScope = placeholderHub.scope(serviceKey, definition.placeholderApiEnabled)
            currencyScope = currencyFeature.scope(serviceKey, placeholderScope)
            serviceScope.register(CurrencyService::class.java, currencyScope)
            serviceScope.register(CurrencyStorageFactory::class.java, currencyFeature.storages)
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
                    serviceKey.value,
                    requireNotNull(definition.diagnosticsDirectory),
                    container,
                )
            }
            definition.updateRequest?.let { request ->
                updateRegistration = updates.register(owner, requireNotNull(productDescriptor), request, definition.dependencies)
            }
            val dependencyDownloads = directDownloads?.registerDependencies(owner, definition.dependencies)
            val fileDownloads = definition.downloadsRequest?.let { request -> directDownloads?.register(owner, request) }
            downloadRegistration = CompositeDownloadRegistration.combine(dependencyDownloads, fileDownloads)
            return Context(
                parent,
                id,
                serviceKey,
                metadata,
                taskScope,
                eventScope,
                serviceScope,
                logging.logger(owner, id.value),
                configScope,
                placeholderScope,
                ComponentServiceImpl(placeholderScope, sharedComponentCache),
                CooldownServiceImpl(),
                currencyScope,
                metricsController,
                diagnosticRegistration,
                updateRegistration,
                downloadRegistration,
                definition.placeholderApiEnabled,
                definition.listeners.size,
                productDescriptor?.id?.value,
            )
        } catch (error: Throwable) {
            ResourceCleanup.suppressInto(
                error,
                { downloadRegistration?.close() },
                { updateRegistration?.close() },
                { diagnosticRegistration?.close() },
                { metricsController?.close() },
                { currencyScope?.close() },
                { placeholderScope?.close() },
                { configScope?.close() },
                { eventScope?.close() },
                { services.unregisterAll(serviceKey) },
                { taskScope?.close() },
            )
            throw error
        }
    }

    private fun detachPlugin(owner: Any): Plugin? = synchronized(plugins) { plugins.remove(owner) }

    private inner class Plugin(
        override val owner: Any,
        private val nativeId: PluginId,
        private val registrationId: Long,
    ) : PluginRegistration {
        private val moduleContexts = linkedMapOf<ModuleId, Context>()
        private val pluginClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = pluginClosed.get()

        override fun registerModule(id: ModuleId, configure: Consumer<PluginBuilder>): ModuleContext =
            synchronized(plugins) { synchronized(moduleContexts) {
                check(!pluginClosed.get()) { "Plugin context is closed" }
                check(!closed.get()) { "PluginRegistry is closed" }
                require(id !in moduleContexts) { "Module $id is already registered for this plugin" }
                val definition = ModuleDefinitionBuilder().also { configure.accept(it) }
                val descriptor = productResolver.resolve(owner, id, definition)
                require(descriptor == null || descriptor.id.value !in productDescriptors) {
                    "Component ${descriptor?.id} is already registered"
                }
                definition.bindUpdatesTo(descriptor)
                dependencyValidator.validate(definition.dependencies, productDescriptors)
                val serviceKey = ModuleServiceKeyFactory.create(registrationId, nativeId, id)
                createContext(this, id, serviceKey, definition, descriptor).also { context ->
                    moduleContexts[id] = context
                    if (descriptor != null) productDescriptors[descriptor.id.value] = descriptor
                    try {
                        definition.remotePolicy?.let(context::startRemotePolicy)
                    } catch (error: Throwable) {
                        moduleContexts.remove(id)
                        context.closeInternal()
                        throw error
                    }
                }
            } }

        override fun getModule(id: ModuleId): ModuleContext? =
            synchronized(moduleContexts) { moduleContexts[id] }

        override fun unregisterModule(id: ModuleId) {
            detach(id)?.closeInternal()
        }

        override fun all(): List<ModuleContext> =
            synchronized(moduleContexts) { java.util.Collections.unmodifiableList(ArrayList(moduleContexts.values)) }

        @Deprecated("Use all()", ReplaceWith("all()"))
        override fun modules(): List<ModuleContext> = all()

        override fun close() {
            detachPlugin(owner)?.closeInternal()
        }

        fun detach(id: ModuleId): Context? = synchronized(moduleContexts) { moduleContexts.remove(id) }

        fun closeInternal() {
            if (!pluginClosed.compareAndSet(false, true)) return
            val current = synchronized(moduleContexts) {
                moduleContexts.values.toList().asReversed().also { moduleContexts.clear() }
            }
            ResourceCleanup.closeAll(
                *current.map { module -> ({ module.closeInternal() }) }.toTypedArray(),
                { commands?.unregisterOwner(owner) },
            )
        }
    }

    private inner class Context(
        private val parent: Plugin,
        override val id: ModuleId,
        override val key: PluginId,
        override val metadata: PluginMetadata,
        override val tasks: TaskScope,
        override val events: EventScope,
        override val services: ServiceManagerImpl.OwnedServices,
        override val logger: PnLogger,
        override val configs: ConfigScope,
        override val placeholders: PlaceholderService,
        override val components: ComponentService,
        override val cooldowns: CooldownService,
        private val currencyScope: CurrencyService,
        override val metrics: MetricsController,
        override val diagnostics: DiagnosticRegistration?,
        override val updates: UpdateRegistration?,
        override val downloads: DownloadRegistration?,
        private val placeholderApiEnabled: Boolean,
        private val listenerCount: Int,
        private val productId: String?,
    ) : ModuleContext {
        private val owner: Any get() = parent.owner
        private val contextClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = contextClosed.get()

        fun startRemotePolicy(policy: ru.privatenull.pnlibrary.api.plugin.RemotePolicy) {
            remotePolicies.start(
                owner = owner,
                metadata = metadata,
                policy = policy,
                tasks = tasks,
                isClosed = { isClosed },
                logger = logger,
                closeModule = ::close,
                closePlugin = parent::close,
            )
        }

        override val lifecycle: PluginLifecycle = object : PluginLifecycle {
            override val metadata: PluginMetadata get() = this@Context.metadata
            override fun enabled(): MessageBox =
                logging.box(owner, metadata.name, metadata.version)
                    .ok("Identifier", id.value)
                    .ok("Platform", ModuleRuntimeSummary.platform(metadata))
                    .status("Metrics", ModuleRuntimeSummary.metrics(metrics.projectId, metrics.isEnabled))
                    .status("Updates", ModuleRuntimeSummary.updates(updates))
                    .status("Diagnostics", if (diagnostics == null) null else "enabled")
                    .status("PlaceholderAPI", ModuleRuntimeSummary.placeholderApi(
                        placeholderApiEnabled,
                        placeholderHub.get("placeholderapi")?.state?.name,
                    ))
                    .status("Events", if (listenerCount == 0) null else "$listenerCount listener(s)")
            override fun disabled(): MessageBox =
                logging.shutdownBox(owner, metadata.name, metadata.version)
                    .status("Resources", if (isClosed) "released" else "close pending")
                    .status("Updates", if (updates == null) null else if (isClosed) "stopped" else "registered")
                    .status("Metrics", if (metrics.projectId == null) null else if (isClosed) "stopped" else
                        ModuleRuntimeSummary.metrics(metrics.projectId, metrics.isEnabled))
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
            ) = ActionContext(player, allPlayers, components, logger, tasks, serializerType, values, placeholders = placeholders)
        }

        override fun close() {
            parent.detach(id)?.closeInternal()
        }

        fun closeInternal() {
            if (!contextClosed.compareAndSet(false, true)) return
            productId?.let { value -> synchronized(plugins) { productDescriptors.remove(value) } }
            ResourceCleanup.closeAll(
                { downloads?.close() },
                { updates?.close() },
                { diagnostics?.close() },
                { this@PluginRegistryImpl.diagnostics.clearPlugin(key.value) },
                { metrics.close() },
                { currencyScope.close() },
                { cooldowns.close() },
                { placeholders.close() },
                { configs.close() },
                { services.close() },
                { events.close() },
                { tasks.close() },
            )
        }

        private fun MessageBox.status(label: String, detail: String?): MessageBox =
            if (detail == null) skip(label, "not configured") else ok(label, detail)

    }

}
