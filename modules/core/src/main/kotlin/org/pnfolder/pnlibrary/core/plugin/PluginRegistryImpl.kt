package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.actions.ActionService
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandError
import org.pnfolder.pnlibrary.api.commands.CommandErrorHandler
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.events.EventScope
import org.pnfolder.pnlibrary.api.events.EventService
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.metrics.MetricsController
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginBuilder
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.plugin.PluginLifecycle
import org.pnfolder.pnlibrary.api.plugin.PluginMessages
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.services.ServiceManager
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.config.ConfigurationRuntime
import org.pnfolder.pnlibrary.core.commands.CommandFeedbackScope
import org.pnfolder.pnlibrary.core.commands.CommandService
import org.pnfolder.pnlibrary.core.downloads.DirectDownloadManager
import org.pnfolder.pnlibrary.core.expansions.LocalizationExpansion
import org.pnfolder.pnlibrary.core.lifecycle.ResourceCleanup
import org.pnfolder.pnlibrary.core.remote.RemotePolicyMonitor
import org.pnfolder.pnlibrary.core.services.ServiceManagerImpl
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
    private val services: ServiceManagerImpl,
    private val logging: LoggingService,
    private val metrics: MetricsService,
    private val diagnostics: DiagnosticsService,
    private val updates: UpdateService,
    private val shared: SharedFeatures,
    private val configurations: ConfigurationRuntime,
    private val commands: CommandService? = null,
    private val directDownloads: DirectDownloadManager? = null,
    libraryVersion: String? = null,
) : PluginRegistry {

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
    ): Context {
        val owner = parent.owner
        val metadata = metadataFactory.create(owner, id, definition)
        val resources = resourceFactory.create(owner, id, serviceKey, definition, productDescriptor)
        return Context(
            parent = parent,
            id = id,
            key = serviceKey,
            metadata = metadata,
            resources = resources,
            placeholderApiEnabled = definition.placeholderApiEnabled,
            productId = productDescriptor?.id?.value,
        )
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

        /** What the whole plugin answers to a denied or mistyped command; each module's scope inherits from it. */
        val commandFeedback = CommandFeedbackScope()
        override fun <E : CommandError> onCommandError(type: Class<E>, handler: CommandErrorHandler<E>) {
            commandFeedback.handlers[type] = handler
        }

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
    ) : ModuleContext {
        private val owner: Any get() = parent.owner
        private val contextClosed = AtomicBoolean(false)
        private val moduleCommands = mutableListOf<CommandRegistration>()
        private val commandFeedback = CommandFeedbackScope(parent.commandFeedback)
        override fun registerCommand(command: CommandDefinition): CommandRegistration =
            synchronized(moduleCommands) {
                check(!isClosed) { "Module is closed" }
                val registration = checkNotNull(commands) { "Commands are unavailable" }.register(owner, command, commandFeedback)
                if (isClosed) {
                    registration.close()
                    error("Module is closed")
                }
                moduleCommands.removeAll { it.isClosed }
                moduleCommands.add(registration)
                registration
            }
        override fun <E : CommandError> onCommandError(type: Class<E>, handler: CommandErrorHandler<E>) {
            commandFeedback.handlers[type] = handler
        }
        override val isClosed: Boolean get() = contextClosed.get()
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

        fun startRemotePolicy(policy: RemotePolicySettings) {
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
        override val actions: ActionService = ModuleActionService(resources)

        override fun close() {
            parent.detach(id)?.closeInternal()
        }

        fun closeInternal() {
            if (!contextClosed.compareAndSet(false, true)) return
            productId?.let { value -> synchronized(plugins) { productDescriptors.remove(value) } }
            val registrations = synchronized(moduleCommands) {
                moduleCommands.toList().also { moduleCommands.clear() }
            }
            ResourceCleanup.closeAll(
                { ResourceCleanup.closeAll(*registrations.asReversed().map { registration -> { registration.close() } }.toTypedArray()) },
                { expansions.close() },
                { resources.close(this@PluginRegistryImpl.diagnostics, key) },
            )
        }
    }
}
