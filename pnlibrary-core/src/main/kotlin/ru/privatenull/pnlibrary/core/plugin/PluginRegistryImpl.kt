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
import ru.privatenull.pnlibrary.api.actions.PlayerActionContext
import ru.privatenull.pnlibrary.api.actions.PlayerActionHandler
import ru.privatenull.pnlibrary.api.actions.PlayerActionRegistration
import ru.privatenull.pnlibrary.api.actions.PlayerActionResult
import ru.privatenull.pnlibrary.api.actions.ActionSequenceResult
import ru.privatenull.pnlibrary.api.actions.PlayerActionAccess
import java.util.UUID
import ru.privatenull.pnlibrary.api.actions.PlayerAction
import ru.privatenull.pnlibrary.api.actions.PlayerActionSequence
import ru.privatenull.pnlibrary.api.actions.PlayerActionService
import ru.privatenull.pnlibrary.api.actions.PlayerActionFlow
import ru.privatenull.pnlibrary.api.actions.DelayedPlayerActionFlow
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.text.ComponentService
import ru.privatenull.pnlibrary.core.placeholders.PlaceholderHub
import ru.privatenull.pnlibrary.core.text.ComponentCache
import ru.privatenull.pnlibrary.core.text.ComponentServiceImpl
import ru.privatenull.pnlibrary.spi.platform.PlatformPlayerAction
import ru.privatenull.pnlibrary.api.cooldowns.CooldownService
import ru.privatenull.pnlibrary.core.cooldowns.CooldownServiceImpl

internal class PluginRegistryImpl(
    private val platform: PlatformAdapter,
    private val events: EventService,
    private val tasks: TaskService,
    private val services: ServiceManagerImpl,
    private val logging: LoggingService,
    private val metrics: MetricsService,
    private val diagnostics: DiagnosticsService,
    private val updates: UpdateService,
    private val configurations: ConfigurationServiceImpl = ConfigurationServiceImpl(platform),
) : PluginRegistry {

    private val contexts = linkedMapOf<PluginId, Context>()
    private val owners = IdentityHashMap<Any, Context>()
    private val closed = AtomicBoolean(false)
    private val placeholderHub = PlaceholderHub(platform)
    private val sharedComponentCache = ComponentCache()
    private val registeredActions = ConcurrentHashMap<String, ActionEntry>()

    private data class ActionEntry(
        val owner: PluginId,
        val handler: String,
        val access: PlayerActionAccess,
        val executor: PlayerActionHandler,
    )

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
        try {
            taskScope = tasks.scope(owner)
            eventScope = events.scope(id)
            configScope = configurations.scope(owner)
            placeholderScope = placeholderHub.scope(id)
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
                metricsController,
                diagnosticRegistration,
                updateRegistration,
                definition.listeners.size,
            )
        } catch (error: Throwable) {
            runCatching { updateRegistration?.close() }
            runCatching { diagnosticRegistration?.close() }
            runCatching { metricsController?.close() }
            runCatching { eventScope?.close() }
            runCatching { configScope?.close() }
            runCatching { placeholderScope?.close() }
            runCatching { services.unregisterAll(id) }
            runCatching { taskScope?.close() }
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
        override val metrics: MetricsController,
        override val diagnostics: DiagnosticRegistration?,
        override val updates: UpdateRegistration?,
        private val listenerCount: Int,
    ) : PluginContext {
        private val contextClosed = AtomicBoolean(false)
        private val builtInActions = ru.privatenull.pnlibrary.api.actions.PlayerActionType.values()
            .map { it.name.lowercase() }.toSet()
        init {
            builtInActions.forEach { key -> registeredActions[actionId(id, key)] = ActionEntry(id, key, PlayerActionAccess.ownerOnly(), PlayerActionHandler { actionContext ->
                val completion = CompletableFuture<PlayerActionResult>()
                platform.executeGlobal(Runnable {
                    runCatching {
                        platform.executePlayerAction(owner, actionContext.playerId, PlatformPlayerAction(
                            actionContext.action, actionContext.message, actionContext.title, actionContext.subtitle,
                        ))
                    }.onSuccess { completion.complete(PlayerActionResult.success()) }
                        .onFailure(completion::completeExceptionally)
                })
                completion
            }) }
        }
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
        override val actions: PlayerActionService = object : PlayerActionService {
            override fun execute(playerId: UUID, sequence: PlayerActionSequence) {
                execute(playerId, sequence, emptyMap())
            }

            override fun execute(playerId: UUID, sequence: PlayerActionSequence, placeholders: Map<String, Any?>) {
                executeAsync(playerId, sequence, placeholders).whenComplete { _, error ->
                    if (error != null) logger.error("Could not execute player action sequence", error)
                }
            }

            override fun executeAsync(playerId: UUID, sequence: PlayerActionSequence, placeholders: Map<String, Any?>): java.util.concurrent.CompletionStage<ActionSequenceResult> {
                check(!isClosed) { "Plugin context $id is closed" }
                return executeSequence(playerId, sequence, placeholders, emptyList())
            }

            override fun register(handler: String, actionHandler: PlayerActionHandler): PlayerActionRegistration {
                return register(handler, PlayerActionAccess.ownerOnly(), actionHandler)
            }

            override fun register(handler: String, access: PlayerActionAccess, actionHandler: PlayerActionHandler): PlayerActionRegistration {
                val key = normalizeHandler(handler)
                require("::" !in key) { "Registered handler key must not contain the owner separator '::'" }
                require(key !in builtInActions) { "Built-in action handler '$key' cannot be replaced" }
                val entry = ActionEntry(id, key, access, actionHandler)
                val registrationId = actionId(id, key)
                require(registeredActions.putIfAbsent(registrationId, entry) == null) { "Action handler '$key' is already registered by $id" }
                return object : PlayerActionRegistration {
                    private val active = AtomicBoolean(true)
                    override val owner = id
                    override val handler = key
                    override val access: PlayerActionAccess = entry.access
                    override val isActive: Boolean get() = active.get()
                    override fun close() { if (active.compareAndSet(true, false)) registeredActions.remove(registrationId, entry) }
                }
            }
        }

        override fun close() {
            synchronized(contexts) {
                unregisterLocked(this)
            }
        }

        fun closeInternal() {
            if (!contextClosed.compareAndSet(false, true)) return
            runCatching { updates?.close() }
            runCatching { diagnostics?.close() }
            runCatching { this@PluginRegistryImpl.diagnostics.clearPlugin(id.value) }
            runCatching { metrics.close() }
            runCatching { events.close() }
            runCatching { services.close() }
            runCatching { configs.close() }
            runCatching { placeholders.close() }
            runCatching { cooldowns.close() }
            registeredActions.entries.removeIf { it.value.owner == id }
            runCatching { tasks.close() }
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

        private fun MessageBox.status(label: String, detail: String?): MessageBox =
            if (detail == null) skip(label, "not configured") else ok(label, detail)

        private fun resolve(action: PlayerAction, values: Map<String, Any?>): PlayerAction {
            fun String?.resolved(): String? = this?.let { source ->
                values.entries.fold(source) { text, (key, value) -> text.replace("{$key}", value?.toString().orEmpty()) }
            }
            return copyAction(action).apply {
                text = action.text.resolved()
                title = action.title.resolved()
                subtitle = action.subtitle.resolved()
                world = action.world.resolved()
                sound = action.sound.resolved()
                command = action.command.resolved()
            }
        }

        private fun renderAsync(action: PlayerAction, playerId: UUID, values: Map<String, Any?>): java.util.concurrent.CompletionStage<PlayerAction> {
            fun rendered(value: String?) = value?.let { placeholders.render(it, playerId, values) }
                ?: CompletableFuture.completedFuture(null)
            val text = rendered(action.text).toCompletableFuture()
            val title = rendered(action.title).toCompletableFuture()
            val subtitle = rendered(action.subtitle).toCompletableFuture()
            val world = rendered(action.world).toCompletableFuture()
            val sound = rendered(action.sound).toCompletableFuture()
            val command = rendered(action.command).toCompletableFuture()
            return CompletableFuture.allOf(text, title, subtitle, world, sound, command).thenApply {
                copyAction(action).apply {
                    this.text = text.join()
                    this.title = title.join()
                    this.subtitle = subtitle.join()
                    this.world = world.join()
                    this.sound = sound.join()
                    this.command = command.join()
                    arguments = action.arguments.mapValuesTo(linkedMapOf()) { (_, value) ->
                    if (value is String) placeholders.render(value, playerId, values).toCompletableFuture().join() else value
                    }
                }
            }
        }

        private fun copyAction(source: PlayerAction) = PlayerAction(source.type).also { target ->
            target.text = source.text
            target.title = source.title
            target.subtitle = source.subtitle
            target.fadeIn = source.fadeIn
            target.stay = source.stay
            target.fadeOut = source.fadeOut
            target.world = source.world
            target.x = source.x
            target.y = source.y
            target.z = source.z
            target.yaw = source.yaw
            target.pitch = source.pitch
            target.sound = source.sound
            target.volume = source.volume
            target.soundPitch = source.soundPitch
            target.command = source.command
            target.arguments = LinkedHashMap(source.arguments)
            target.payload = source.payload
            target.duration = source.duration
            target.actions = source.actions.toMutableList()
        }

        private fun platformAction(action: PlayerAction) = PlatformPlayerAction(
            source = action,
            text = action.text?.let(components::deserialize),
            title = action.title?.let(components::deserialize),
            subtitle = action.subtitle?.let(components::deserialize),
        )

        private fun executeSequence(
            playerId: UUID,
            sequence: PlayerActionSequence,
            placeholders: Map<String, Any?>,
            stack: List<String>,
        ): java.util.concurrent.CompletionStage<ActionSequenceResult> {
            var chain: java.util.concurrent.CompletionStage<List<PlayerActionResult>> = CompletableFuture.completedFuture(emptyList())
            sequence.actions.map { resolve(it, placeholders) }.forEach { action ->
                chain = chain.thenCompose { results ->
                    renderAsync(action, playerId, placeholders).thenCompose { rendered ->
                        executeOne(playerId, rendered, placeholders, stack).thenApply { results + it }
                    }
                }
            }
            return chain.thenApply(::ActionSequenceResult)
        }

        private fun executeOne(
            playerId: UUID,
            action: PlayerAction,
            values: Map<String, Any?>,
            stack: List<String>,
        ): java.util.concurrent.CompletionStage<PlayerActionResult> {
            val reference = normalizeHandler(action.type)
            if (reference == "delay") {
                require(!action.duration.isNegative) { "Action delay must not be negative" }
                require(action.actions.isNotEmpty()) { "Delay action requires at least one child action" }
                require(stack.size < 32) { "Player action nesting exceeds 32 calls" }
                val result = CompletableFuture<PlayerActionResult>()
                tasks.later(action.duration, Runnable {
                    if (isClosed) {
                        result.completeExceptionally(IllegalStateException("Plugin context $id is closed"))
                    } else {
                        executeSequence(playerId, PlayerActionSequence(action.actions), values, stack + "delay[${stack.size}]")
                            .whenComplete { sequence, error ->
                                if (error != null) result.completeExceptionally(error)
                                else if (sequence.successful) result.complete(PlayerActionResult.success())
                                else result.complete(PlayerActionResult.skipped("A delayed child action failed"))
                            }
                    }
                })
                return result
            }
            val split = reference.indexOf("::")
            val actionOwner = if (split > 0) PluginId.of(reference.substring(0, split)) else id
            val key = if (split > 0) reference.substring(split + 2) else reference
            val rendered = platformAction(action)
            val registration = registeredActions[actionId(actionOwner, key)]
                ?: error("Unknown player action handler '$reference' for plugin $id")
            require(registration.access.allows(registration.owner, id)) {
                "Plugin $id cannot execute action ${registration.owner}::$key"
            }
            val qualified = actionId(registration.owner, key)
            require(qualified !in stack) { "Recursive player action call: ${(stack + qualified).joinToString(" -> ")}" }
            require(stack.size < 32) { "Player action nesting exceeds 32 calls" }
            val nextStack = stack + qualified
            val flow = object : PlayerActionFlow {
                override fun execute(action: PlayerAction) =
                    executeSequence(playerId, PlayerActionSequence(mutableListOf(action)), values, nextStack)
                        .thenApply { it.actions.single() }

                override fun execute(sequence: PlayerActionSequence) =
                    executeSequence(playerId, sequence, values, nextStack)

                override fun after(delay: java.time.Duration): DelayedPlayerActionFlow {
                    require(!delay.isNegative) { "Action delay must not be negative" }
                    return object : DelayedPlayerActionFlow {
                        override fun execute(action: PlayerAction) = deferred(delay) {
                            executeSequence(playerId, PlayerActionSequence(mutableListOf(action)), values, nextStack)
                                .thenApply { it.actions.single() }
                        }
                        override fun execute(sequence: PlayerActionSequence) = deferred(delay) {
                            executeSequence(playerId, sequence, values, nextStack)
                        }
                    }
                }

                private fun <T> deferred(
                    delay: java.time.Duration,
                    operation: () -> java.util.concurrent.CompletionStage<T>,
                ): java.util.concurrent.CompletionStage<T> {
                    val result = CompletableFuture<T>()
                    tasks.later(delay, Runnable {
                        if (isClosed) {
                            result.completeExceptionally(IllegalStateException("Plugin context $id is closed"))
                        } else {
                            runCatching(operation).fold(
                                { stage -> stage.whenComplete { value, error ->
                                    if (error == null) result.complete(value) else result.completeExceptionally(error)
                                } },
                                result::completeExceptionally,
                            )
                        }
                    })
                    return result
                }
            }
            return registration.executor.execute(PlayerActionContext(
                registration.owner, playerId, key, action, rendered.text, rendered.title, rendered.subtitle,
                action.arguments.toMap(), action.payload, flow,
            ))
        }

        private fun normalizeHandler(value: String): String = value.trim().lowercase().also {
            require(it.matches(Regex("[a-z0-9_.:-]+(?:::[a-z0-9_.:-]+)?"))) { "Invalid action handler: $value" }
        }

        private fun actionId(owner: PluginId, handler: String) = "${owner.value}::$handler"
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
        val listeners = mutableListOf<Listener>()

        override fun metadata(configure: Consumer<PluginMetadataBuilder>): PluginBuilder = apply {
            configure.accept(MetadataBuilder(this))
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
