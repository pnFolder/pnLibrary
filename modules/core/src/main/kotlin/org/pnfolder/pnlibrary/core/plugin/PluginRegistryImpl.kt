package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.error.CommandError
import org.pnfolder.pnlibrary.api.commands.error.CommandErrorHandler
import org.pnfolder.pnlibrary.api.compat.MinecraftCompatibility
import org.pnfolder.pnlibrary.api.compat.MinecraftSupportRule
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.events.bus.EventScope
import org.pnfolder.pnlibrary.api.events.bus.EventService
import org.pnfolder.pnlibrary.api.libraries.ModuleLibraries
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.metrics.MetricsController
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginBuilder
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry
import org.pnfolder.pnlibrary.api.plugin.control.PluginModule
import org.pnfolder.pnlibrary.api.plugin.lifecycle.PluginLifecycle
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMessages
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.scenario.actions.ActionService
import org.pnfolder.pnlibrary.api.services.ServiceManager
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.config.ConfigurationRuntime
import org.pnfolder.pnlibrary.core.commands.engine.CommandFeedbackScope
import org.pnfolder.pnlibrary.core.commands.engine.CommandService
import org.pnfolder.pnlibrary.core.downloads.fetch.DirectDownloads
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup
import org.pnfolder.pnlibrary.core.expansions.LocalizationExpansion
import org.pnfolder.pnlibrary.core.plugin.module.LibraryFetcher
import org.pnfolder.pnlibrary.core.plugin.module.MinecraftGate
import org.pnfolder.pnlibrary.core.plugin.module.ModuleActionService
import org.pnfolder.pnlibrary.core.plugin.module.ModuleCommands
import org.pnfolder.pnlibrary.core.plugin.module.ModuleController
import org.pnfolder.pnlibrary.core.plugin.module.ModuleDefinitionBuilder
import org.pnfolder.pnlibrary.core.plugin.module.ModuleDependencyValidator
import org.pnfolder.pnlibrary.core.plugin.module.ModuleExpansions
import org.pnfolder.pnlibrary.core.plugin.module.ModuleLibrariesImpl
import org.pnfolder.pnlibrary.core.plugin.module.ModuleLifecycleReporter
import org.pnfolder.pnlibrary.core.plugin.module.ModuleMessages
import org.pnfolder.pnlibrary.core.plugin.module.ModuleMetadataFactory
import org.pnfolder.pnlibrary.core.plugin.module.ModulePreferences
import org.pnfolder.pnlibrary.core.plugin.module.ModuleProductResolver
import org.pnfolder.pnlibrary.core.plugin.module.ModuleResourceFactory
import org.pnfolder.pnlibrary.core.plugin.module.ModuleResources
import org.pnfolder.pnlibrary.core.plugin.module.ScenarioDefinitions
import org.pnfolder.pnlibrary.core.plugin.module.moduleDiagnosticsName
import org.pnfolder.pnlibrary.core.plugin.module.moduleServiceKey
import org.pnfolder.pnlibrary.core.remote.RemotePolicyMonitor
import org.pnfolder.pnlibrary.core.services.ServiceRegistry
import org.pnfolder.pnlibrary.core.tasks.TaskService
import org.pnfolder.pnlibrary.core.text.ComponentCache
import org.pnfolder.pnlibrary.localization.MinecraftLocalization
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Owns plugin-scoped library services and coordinates their lifecycle.
 *
 * Registration behaves transactionally: a context becomes visible only after
 * all requested services have been created. If any step fails, previously
 * created resources are closed in reverse dependency order.
 */
internal class PluginRegistryImpl(
    private val ownership: PlatformOwnership,
    private val identity: PlatformIdentity,
    private val remotePolicies: RemotePolicyMonitor,
    private val events: EventService,
    private val tasks: TaskService,
    private val services: ServiceRegistry,
    private val logging: LoggingService,
    private val metrics: MetricsService,
    private val diagnostics: DiagnosticsService,
    private val updates: UpdateService,
    private val shared: SharedFeatures,
    private val configurations: ConfigurationRuntime,
    private val commands: CommandService? = null,
    private val directDownloads: DirectDownloads? = null,
    libraryVersion: String? = null,
    private val modulePreferences: ModulePreferences = ModulePreferences(),
    private val libraryFetcher: LibraryFetcher = LibraryFetcher.THROUGH_LIBBY,
) : PluginRegistry {

    private val scenarioDefinitions = ScenarioDefinitions()

    /** Every named action, requirement and function of every module. */
    fun scenarioDefinitionInfos() = scenarioDefinitions.infos()
    private val plugins = IdentityHashMap<Any, Plugin>()
    private val productDescriptors = linkedMapOf<String, ProductDescriptor>().apply {
        libraryVersion?.let(SemanticVersion::tryParse)?.let { put(PnLibraryIdentity.PRODUCT_ID, ProductDescriptor.library(it.toString())) }
    }
    private val closed = AtomicBoolean(false)
    private val registrationSequence = AtomicLong()
    private val sharedComponentCache = ComponentCache()
    private val productResolver = ModuleProductResolver(ownership)
    private val metadataFactory = ModuleMetadataFactory(ownership, identity)
    private val resourceFactory = ModuleResourceFactory(
        events, tasks, services, logging, metrics, diagnostics, updates,
        shared.placeholders, shared.currency, configurations, directDownloads, sharedComponentCache,
    )
    private val dependencyValidator = ModuleDependencyValidator(ownership) {
        directDownloads != null
    }
    override fun register(owner: Any): PluginRegistration = synchronized(plugins) {
        check(!closed.get()) { "PluginRegistry is closed" }
        require(ownership.acceptsOwner(owner)) {
            "Object ${owner.javaClass.name} is not a supported owner for ${identity.type.id}"
        }
        require(!plugins.containsKey(owner)) { "This platform plugin is already registered" }
        val details = ownership.ownerDetails(owner)
        val nativeId = details["id"] ?: details["name"]
            ?: error("The platform did not expose a plugin ID for ${owner.javaClass.name}")
        Plugin(owner, PluginId.of(nativeId), registrationSequence.incrementAndGet()).also { plugins[owner] = it }
    }

    override fun get(owner: Any): PluginRegistration? = synchronized(plugins) { plugins[owner] }

    override fun unregister(owner: Any) {
        detachPlugin(owner)?.closeInternal()
    }

    override fun all(): List<PluginRegistration> =
        synchronized(plugins) { Collections.unmodifiableList(ArrayList(plugins.values)) }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(plugins) {
            plugins.values.toList().also {
                plugins.clear()
                productDescriptors.clear()
            }
        }
        ResourceCleanup.closeAll(
            *current.map { plugin -> ({ plugin.closeInternal() }) }.toTypedArray(),
            { directDownloads?.close() },
        )
    }

    private fun createContext(
        parent: Plugin,
        id: ModuleId,
        serviceKey: PluginId,
        definition: ModuleDefinitionBuilder,
        productDescriptor: ProductDescriptor?,
        module: PluginModule?,
        gate: MinecraftGate,
    ): Context {
        val owner = parent.owner
        val metadata = metadataFactory.create(owner, id, definition)
        val resources = resourceFactory.create(owner, id, serviceKey, parent.nativeId, definition, productDescriptor)
        return Context(
            parent = parent,
            id = id,
            key = serviceKey,
            metadata = metadata,
            resources = resources,
            placeholderApiEnabled = definition.placeholderApiEnabled,
            productId = productDescriptor?.id?.value,
            module = module,
            minecraftGate = gate,
        )
    }

    private fun detachPlugin(owner: Any): Plugin? = synchronized(plugins) { plugins.remove(owner) }

    private inner class Plugin(
        override val owner: Any,
        val nativeId: PluginId,
        private val registrationId: Long,
    ) : PluginRegistration {
        override val id: PluginId get() = nativeId
        private val moduleContexts = linkedMapOf<ModuleId, Context>()
        override val minecraftRule = MinecraftSupportRule()
        override val libraries: ModuleLibraries = ModuleLibrariesImpl.through(
            "p-${nativeId.value}-$registrationId", PluginRegistryImpl::class.java.classLoader, owner, null, libraryFetcher, identity.type,
            modulePreferences, nativeId.value,
        )
        private val pluginClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = pluginClosed.get()

        /** What the whole plugin answers to a denied or mistyped command; each module's scope inherits from it. */
        val commandFeedback = CommandFeedbackScope()
        override fun <E : CommandError> onCommandError(type: Class<E>, handler: CommandErrorHandler<E>) {
            commandFeedback.handlers[type] = handler
        }

        override fun registerModule(id: ModuleId, configure: Consumer<PluginBuilder>): ModuleContext =
            start(id, null, configure)

        override fun registerModule(id: ModuleId, module: PluginModule, configure: Consumer<PluginBuilder>): ModuleContext =
            start(id, module, configure)

        private fun start(id: ModuleId, module: PluginModule?, configure: Consumer<PluginBuilder>): ModuleContext =
            register(id, module, configure).also { (it as Context).control.start() }

        private fun register(id: ModuleId, module: PluginModule?, configure: Consumer<PluginBuilder>): ModuleContext =
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
                val serviceKey = moduleServiceKey(registrationId, nativeId, id)
                val gate = MinecraftGate.open(
                    nativeId.value + ":" + id.value, modulePreferences, definition.minecraftRule, minecraftRule, identity.minecraftVersion,
                )
                if (gate.mustFail(module != null)) {
                    throw IllegalStateException("${gate.reason()} Запустить всё равно: /pn modules mc force ${nativeId.value}:${id.value} on")
                }
                createContext(this, id, serviceKey, definition, descriptor, module, gate).also { context ->
                    moduleContexts[id] = context
                    if (descriptor != null) productDescriptors[descriptor.id.value] = descriptor
                    try {
                        (gate.warning ?: gate.reason()?.takeIf { gate.forced })?.let { context.logger.warning(it) }
                        loadDeclaredLibraries(context, definition)
                        definition.remotePolicy?.let(context::startRemotePolicy)
                    } catch (error: Throwable) {
                        moduleContexts.remove(id)
                        context.closeInternal()
                        throw error
                    }
                }
            } }

        /** Loads the libraries the module declared in its builder; an optional one that fails only warns. */
        private fun loadDeclaredLibraries(context: Context, definition: ModuleDefinitionBuilder) {
            for ((library, enabled) in definition.libraries) {
                if (!enabled.asBoolean) continue
                try {
                    context.libraries.load(library)
                } catch (failure: IllegalStateException) {
                    if (library.required) throw failure
                    context.logger.warning("Optional library ${library.coordinates} is not loaded: ${failure.message}")
                }
            }
        }

        override fun getModule(id: ModuleId): ModuleContext? =
            synchronized(moduleContexts) { moduleContexts[id] }

        override fun unregisterModule(id: ModuleId) {
            detach(id)?.closeInternal()
        }

        override fun all(): List<ModuleContext> =
            synchronized(moduleContexts) { Collections.unmodifiableList(ArrayList(moduleContexts.values)) }

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
        private val resources: ModuleResources,
        private val placeholderApiEnabled: Boolean,
        private val productId: String?,
        module: PluginModule?,
        val minecraftGate: MinecraftGate,
    ) : ModuleContext, org.pnfolder.pnlibrary.core.plugin.module.MinecraftForcible {
        override fun forceMinecraft(on: Boolean) = minecraftGate.setForced(on)
        override val minecraft: MinecraftCompatibility get() = minecraftGate.compatibility()
        private val owner: Any get() = parent.owner
        private val contextClosed = AtomicBoolean(false)
        private val commandFeedback = CommandFeedbackScope(parent.commandFeedback)
        private val moduleCommands = ModuleCommands(owner, commands, commandFeedback) { isClosed }
        override fun registerCommand(command: CommandDefinition): CommandRegistration = moduleCommands.register(command)
        override fun <E : CommandError> onCommandError(type: Class<E>, handler: CommandErrorHandler<E>) {
            commandFeedback.handlers[type] = handler
        }
        override val isClosed: Boolean get() = contextClosed.get()
        override val libraries = ModuleLibrariesImpl.through(
            key.value, PluginRegistryImpl::class.java.classLoader, parent.owner, parent.libraries, libraryFetcher, identity.type,
            modulePreferences, parent.nativeId.value + ":" + id.value,
        )
        override val control = ModuleController(key.value, module, modulePreferences, { minecraftGate.reason() }) {
            resources.tasks.cancelAll()
            moduleCommands.drain().asReversed().forEach { it.close() }
        }.also { it.bind(this) }
        override val tasks: TaskScope get() = resources.tasks
        override val events: EventScope get() = resources.events
        override val services: ServiceManager get() = resources.services
        override val expansions = ModuleExpansions(shared.expansions, shared.libraryExpansions)
        override val localization: MinecraftLocalization get() = expansions.view(LocalizationExpansion::class.java)
        override val logger: PnLogger get() = resources.logger
        override val configs: ConfigScope get() = resources.configs
        override val placeholders: PlaceholderService get() = resources.placeholders
        override val components: ComponentService get() = resources.components
        override val cooldowns: CooldownService get() = resources.cooldowns
        override val metrics: MetricsController get() = resources.metrics
        override val errors get() = resources.errors
        override val diagnostics: DiagnosticRegistration? get() = resources.diagnostics
        override val updates: UpdateRegistration? get() = resources.updates
        override val downloads: DownloadRegistration? get() = resources.downloads

        fun startRemotePolicy(policy: RemotePolicySettings) = remotePolicies.start(owner, this, policy, parent::close)
        override val lifecycle: PluginLifecycle = ModuleLifecycleReporter(
            owner = owner,
            moduleId = id,
            metadata = metadata,
            resources = resources,
            logging = logging,
            placeholders = shared.placeholders,
            placeholderApiEnabled = placeholderApiEnabled,
            isClosed = { isClosed },
        )
        override val messages: PluginMessages = ModuleMessages(owner, logging)
        override val actions: ActionService = ModuleActionService(resources, scenarioDefinitions, moduleDiagnosticsName(key, id))

        override fun close() {
            parent.detach(id)?.closeInternal()
        }

        fun closeInternal() {
            if (!contextClosed.compareAndSet(false, true)) return
            control.stop()
            productId?.let { value -> synchronized(plugins) { productDescriptors.remove(value) } }
            val registrations = moduleCommands.drain()
            ResourceCleanup.closeAll(
                { ResourceCleanup.closeAll(*registrations.asReversed().map { registration -> { registration.close() } }.toTypedArray()) },
                { expansions.close() },
                { scenarioDefinitions.removeOwner(moduleDiagnosticsName(key, id)) },
                { resources.close(this@PluginRegistryImpl.diagnostics) },
            )
        }
    }
}
