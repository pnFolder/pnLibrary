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
import ru.privatenull.pnlibrary.api.plugin.PluginRegistration
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.plugin.ModuleContext
import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginLifecycle
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.plugin.PluginMetadataBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginMessages
import ru.privatenull.pnlibrary.api.plugin.PluginOptions
import ru.privatenull.pnlibrary.api.plugin.PluginRegistry
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskExecution
import ru.privatenull.pnlibrary.api.tasks.TaskSpec
import ru.privatenull.pnlibrary.api.plugin.DenyAction
import ru.privatenull.pnlibrary.core.remote.RemotePolicyEngine
import ru.privatenull.pnlibrary.api.tasks.TaskService
import ru.privatenull.pnlibrary.core.tasks.TaskServiceImpl
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.updates.UpdateService
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.update.EmbeddedDescriptorReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarFile
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl
import ru.privatenull.pnlibrary.core.config.ConfigurationServiceImpl
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
import ru.privatenull.pnlibrary.api.downloads.PluginDownloads
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.currency.CurrencyFeature
import ru.privatenull.pnlibrary.core.downloads.DirectDownloadManager

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
    private val sharedComponentCache = ComponentCache()
    override fun register(owner: Any): PluginRegistration = synchronized(plugins) {
        check(!closed.get()) { "PluginRegistry is closed" }
        require(platform.acceptsOwner(owner)) {
            "Object ${owner.javaClass.name} is not a supported owner for ${platform.type.id}"
        }
        require(!plugins.containsKey(owner)) { "This platform plugin is already registered" }
        val details = platform.ownerDetails(owner)
        val nativeId = details["id"] ?: details["name"]
            ?: error("The platform did not expose a plugin ID for ${owner.javaClass.name}")
        Plugin(owner, PluginId.of(nativeId)).also { plugins[owner] = it }
    }

    override fun get(owner: Any): PluginRegistration? = synchronized(plugins) { plugins[owner] }

    override fun unregister(owner: Any) {
        detachPlugin(owner)?.closeInternal()
    }

    override fun registrations(): List<PluginRegistration> =
        synchronized(plugins) { plugins.values.toList() }

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
        definition: Builder,
        productDescriptor: ProductDescriptor?,
    ): Context {
        val owner = parent.owner
        val metadata = metadata(owner, id, definition)
        var taskScope: TaskScope? = null
        var eventScope: EventScope? = null
        var configScope: ConfigScope? = null
        var metricsController: MetricsControllerImpl? = null
        var diagnosticRegistration: DiagnosticRegistration? = null
        var updateRegistration: UpdateRegistration? = null
        var downloadRegistration: DownloadRegistration? = null
        var placeholderScope: PlaceholderService? = null
        var currencyScope: CurrencyService? = null
        try {
            taskScope = if (tasks is TaskServiceImpl) tasks.scope(owner, serviceKey) else tasks.scope(owner)
            eventScope = events.scope(serviceKey)
            configScope = configurations.scope(owner, serviceKey)
            placeholderScope = placeholderHub.scope(serviceKey, definition.placeholderApiEnabled)
            currencyScope = currencyFeature.scope(serviceKey, placeholderScope)
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
            definition.downloadsRequest?.let { request ->
                downloadRegistration = directDownloads?.register(owner, request)
            }
            return Context(
                parent,
                id,
                serviceKey,
                metadata,
                taskScope,
                eventScope,
                services.ownedBy(serviceKey),
                logging.logger(owner, id.value),
                configScope,
                placeholderScope,
                ComponentServiceImpl(placeholderScope, sharedComponentCache),
                CooldownServiceImpl(),
                currencyScope,
                currencyFeature.storages,
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
                { updateRegistration?.close() },
                { downloadRegistration?.close() },
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

    private fun metadata(owner: Any, id: ModuleId, definition: Builder): PluginMetadata {
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

    private fun detachPlugin(owner: Any): Plugin? = synchronized(plugins) { plugins.remove(owner) }

    private fun serviceKey(nativeId: PluginId, moduleId: ModuleId): PluginId {
        val canonical = "${nativeId.value}\u0000${moduleId.value}"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return PluginId.of(
            "m-${nativeId.value.take(18)}-${moduleId.value.take(18)}-${hash.take(16)}",
        )
    }

    private fun productDescriptor(owner: Any, id: ModuleId, definition: Builder): ProductDescriptor? {
        val embedded = embeddedDescriptor(owner)
        val explicit = definition.productDescriptor
        if (embedded != null && explicit != null) {
            require(embedded.id == explicit.id && embedded.version == explicit.version &&
                embedded.supportedApi == explicit.supportedApi) {
                "Explicit component descriptor conflicts with embedded metadata for $id"
            }
        }
        val inferred = definition.updateRequest?.let { request ->
            val version = platform.ownerDetails(owner)["version"] ?: definition.metadataVersion
                ?: error("Cannot infer component version for $id")
            ProductDescriptor.builder(id.value, version)
                .pnLibraryApi(request.supportedApi.minimum, request.supportedApi.maximum)
                .build()
        }
        val implicit = if (explicit == null && embedded == null && inferred == null && definition.dependencies.isNotEmpty()) {
            val version = platform.ownerDetails(owner)["version"] ?: definition.metadataVersion
                ?: error("Cannot infer component version for $id")
            ProductDescriptor.builder(id.value, version)
                .pnLibraryApi(PnLibraryApi.VERSION, PnLibraryApi.VERSION)
                .build()
        } else null
        return (explicit ?: embedded ?: inferred ?: implicit)?.also {
            require(it.id.value == id.value) { "Component ID ${it.id} does not match plugin ID $id" }
        }
    }

    private fun embeddedDescriptor(owner: Any): ProductDescriptor? {
        val location = runCatching {
            Paths.get(owner.javaClass.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
        }.getOrNull() ?: return null
        if (!Files.isRegularFile(location)) return null
        val present = runCatching { JarFile(location.toFile()).use { it.getJarEntry(EmbeddedDescriptorReader.ENTRY) != null } }
            .getOrElse { throw IllegalArgumentException("Cannot inspect component metadata", it) }
        return if (present) EmbeddedDescriptorReader().read(location) else null
    }

    private fun validateDependencies(dependencies: List<PluginDependency>, downloads: PluginDownloads?) {
        val downloadableComponents = downloads?.declarations.orEmpty()
            .filterIsInstance<ru.privatenull.pnlibrary.api.downloads.DownloadDeclaration.Component>()
            .associateBy { it.component }
        val downloadablePlugins = downloads?.declarations.orEmpty()
            .filterIsInstance<ru.privatenull.pnlibrary.api.downloads.DownloadDeclaration.Plugin>()
            .associateBy { it.plugin.lowercase() }
        val problems = dependencies.mapNotNull { it.managed }.filter { it.required }.mapNotNull { dependency ->
            val installed = productDescriptors[dependency.product.value]
            when {
                installed == null && downloadableComponents[dependency.product]?.let { it.version >= dependency.minimumVersion } == true -> null
                installed == null -> "${dependency.product} >= ${dependency.minimumVersion} is missing (${dependency.repositoryOwner}/${dependency.repositoryName})"
                installed.version < dependency.minimumVersion -> "${dependency.product} ${installed.version} is installed, ${dependency.minimumVersion} is required"
                else -> null
            }
        }.toMutableList()
        val nativePlugins = runCatching { platform.installedPlugins() }.getOrNull().orEmpty()
            .entries.associate { it.key.lowercase() to it.value }
        dependencies.mapNotNull { it.external }.filter { it.required }.forEach { dependency ->
            val installed = nativePlugins[dependency.plugin.lowercase()]
            when {
                installed == null && downloadablePlugins[dependency.plugin.lowercase()]?.let {
                    it.minimumVersion >= dependency.minimumVersion
                } == true -> null
                installed == null -> problems += "${dependency.plugin} >= ${dependency.minimumVersion} is missing (${dependency.downloadPage ?: "no download page"})"
                SemanticVersion.tryParse(installed)?.let { it >= dependency.minimumVersion } != true ->
                    problems += "${dependency.plugin} $installed is installed, ${dependency.minimumVersion} is required"
            }
        }
        require(problems.isEmpty()) { "Unsatisfied pnLibrary component dependencies: ${problems.joinToString("; ")}" }
    }

    private inner class Plugin(
        override val owner: Any,
        private val nativeId: PluginId,
    ) : PluginRegistration {
        private val moduleContexts = linkedMapOf<ModuleId, Context>()
        private val pluginClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = pluginClosed.get()

        override fun registerModule(id: ModuleId, configure: Consumer<PluginBuilder>): ModuleContext =
            synchronized(plugins) { synchronized(moduleContexts) {
                check(!pluginClosed.get()) { "Plugin context is closed" }
                check(!closed.get()) { "PluginRegistry is closed" }
                require(id !in moduleContexts) { "Module $id is already registered for this plugin" }
                val definition = Builder().also { configure.accept(it) }
                definition.materializeDependencyDownloads()
                val descriptor = productDescriptor(owner, id, definition)
                require(descriptor == null || descriptor.id.value !in productDescriptors) {
                    "Component ${descriptor?.id} is already registered"
                }
                definition.bindUpdatesTo(descriptor)
            validateDependencies(definition.dependencies, definition.downloadsRequest)
                createContext(this, id, serviceKey(nativeId, id), definition, descriptor).also { context ->
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

        override fun modules(): List<ModuleContext> =
            synchronized(moduleContexts) { moduleContexts.values.toList() }

        override fun close() {
            detachPlugin(owner)?.closeInternal()
        }

        fun detach(id: ModuleId): Context? = synchronized(moduleContexts) { moduleContexts.remove(id) }

        fun closeInternal() {
            if (!pluginClosed.compareAndSet(false, true)) return
            val current = synchronized(moduleContexts) {
                moduleContexts.values.toList().also { moduleContexts.clear() }
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
        override val currencies: CurrencyService,
        override val currencyStorages: CurrencyStorageFactory,
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
            val remoteContext = platform.remotePolicyContext(owner, metadata, policy.values)
            tasks.schedule(TaskSpec.builder()
                .name("remote policy: ${id.value}")
                .execution(TaskExecution.async())
                .interval(policy.checkEvery)
                .action {
                    if (isClosed) return@action
                    try {
                        val decision = RemotePolicyEngine.check(policy.source, remoteContext)
                        if (!decision.allowed) {
                            platform.executeGlobal(Runnable {
                                if (isClosed) return@Runnable
                                logger.warning("Remote policy denied ${metadata.name}: ${decision.message}")
                                if (policy.onDeny == DenyAction.DISABLE_MODULE) {
                                    close()
                                } else {
                                    if (!platform.disableOwner(owner)) parent.close()
                                }
                            })
                        }
                    } catch (error: Throwable) {
                        platform.executeGlobal(Runnable {
                            if (isClosed) return@Runnable
                            logger.error("Remote policy failed for ${metadata.name}", error)
                            if (policy.onDeny == DenyAction.DISABLE_MODULE) close()
                            else if (!platform.disableOwner(owner)) parent.close()
                        })
                    }
                }.build())
        }

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
            ) = ActionContext(player, allPlayers, components, logger, tasks, serializerType, values, placeholders = placeholders)
        }

        override fun close() {
            parent.detach(id)?.closeInternal()
        }

        fun closeInternal() {
            if (!contextClosed.compareAndSet(false, true)) return
            productId?.let { value -> synchronized(plugins) { productDescriptors.remove(value) } }
            ResourceCleanup.closeAll(
                { updates?.close() },
                { downloads?.close() },
                { diagnostics?.close() },
                { this@PluginRegistryImpl.diagnostics.clearPlugin(key.value) },
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
        var remotePolicy: ru.privatenull.pnlibrary.api.plugin.RemotePolicy? = null
        var metadataName: String? = null
        var metadataVersion: String? = null
        var metadataAuthors: String? = null
        var metricsProjectId: Int? = null
        var metricsEnabled: Boolean = false
        val metricsConfigurers = mutableListOf<Consumer<PluginMetrics>>()
        var diagnosticsDirectory: Path? = null
        var diagnosticContainer: DiagnosticContainer? = null
        var updateRequest: PluginUpdateRequest? = null
        var productDescriptor: ProductDescriptor? = null
        val dependencies = mutableListOf<PluginDependency>()
        var downloadsRequest: PluginDownloads? = null
        var placeholderApiEnabled: Boolean = true
        val listeners = mutableListOf<Listener>()

    override fun product(descriptor: ProductDescriptor): PluginBuilder = apply {
            require(productDescriptor == null) { "component descriptor is already configured" }
            productDescriptor = descriptor
        }

        override fun depends(dependency: PluginDependency): PluginBuilder = apply {
            require(dependency.managed != null || dependency.external != null) {
                "plugin dependency must provide a managed or external dependency"
            }
            require(dependencies.none { existing ->
                (existing.managed?.product == dependency.managed?.product && dependency.managed != null) ||
                    (existing.external?.plugin?.equals(dependency.external?.plugin, true) == true && dependency.external != null)
            }) { "duplicate plugin dependency" }
            dependencies += dependency
        }

        fun materializeDependencyDownloads() {
            if (downloadsRequest != null) return
            val automatic = dependencies.mapNotNull { dependency ->
                val external = dependency.external ?: return@mapNotNull null
                val artifact = external.artifact ?: return@mapNotNull null
                if (!dependency.automaticDownload) return@mapNotNull null
                external to artifact
            }
            if (automatic.isEmpty()) return
            val builder = PluginDownloads.builder()
            automatic.forEach { (external, artifact) ->
                builder.plugin(external.plugin) { declaration ->
                    declaration.minimumVersion(external.minimumVersion.toString())
                        .url(artifact.uri.toString())
                        .required(external.required)
                        .automaticDownload(true)
                        .forceAutomaticDownload(external.forceAutomaticDownload)
                        .integrity(artifact.size, artifact.sha256)
                }
            }
            downloadsRequest = builder.build()
        }

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

        override fun downloads(request: PluginDownloads): PluginBuilder = apply {
            require(downloadsRequest == null) { "downloads are already configured" }
            downloadsRequest = request
        }

        override fun remotePolicy(configure: Consumer<ru.privatenull.pnlibrary.api.plugin.RemotePolicyBuilder>): PluginBuilder = apply {
            val builder = ru.privatenull.pnlibrary.api.plugin.RemotePolicyBuilder()
            configure.accept(builder)
            remotePolicy = builder.build()
        }

        @Deprecated("Register listeners through ModuleContext.events after the context has been created")
        override fun listener(listener: Listener): PluginBuilder = apply {
            listeners += listener
        }

        fun bindUpdatesTo(descriptor: ProductDescriptor?) {
            val source = updateRequest ?: return
            if (descriptor == null) return
            updateRequest = PluginUpdateRequest.builder()
                .repository(source.repositoryOwner, source.repositoryName)
                .channel(source.channel)
                .automaticDownload(source.automaticDownload)
                .supportedApi(descriptor.supportedApi.minimum, descriptor.supportedApi.maximum)
                .also { target ->
                    source.artifacts.forEach {
                        val artifactPlatform = it.platform
                        if (artifactPlatform == null) target.artifact(it.pattern, it.minimumJava, it.maximumJava)
                        else target.artifact(it.pattern, artifactPlatform, it.minimumJava, it.maximumJava)
                    }
                }
                .build()
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
