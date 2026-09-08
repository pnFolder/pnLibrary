package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.events.EventService
import ru.privatenull.pnlibrary.api.events.Listener
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.logging.MessageBox
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.plugin.MetricsController
import ru.privatenull.pnlibrary.api.plugin.PluginBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginContext
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.plugin.PluginRegistry
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskService
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.updates.UpdateService
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

internal class PluginRegistryImpl(
    private val events: EventService,
    private val tasks: TaskService,
    private val logging: LoggingService,
    private val metrics: MetricsService,
    private val diagnostics: DiagnosticsService,
    private val updates: UpdateService,
) : PluginRegistry {

    private val contexts = linkedMapOf<PluginId, Context>()
    private val owners = IdentityHashMap<Any, Context>()
    private val closed = AtomicBoolean(false)

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
        var taskScope: TaskScope? = null
        var eventScope: EventScope? = null
        var metricsController: MetricsControllerImpl? = null
        var diagnosticRegistration: DiagnosticRegistration? = null
        var updateRegistration: UpdateRegistration? = null
        try {
            taskScope = tasks.scope(owner)
            eventScope = events.scope(id)
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
                taskScope,
                eventScope,
                logging.logger(owner, id.value),
                metricsController,
                diagnosticRegistration,
                updateRegistration,
            )
        } catch (error: Throwable) {
            runCatching { updateRegistration?.close() }
            runCatching { diagnosticRegistration?.close() }
            runCatching { metricsController?.close() }
            runCatching { eventScope?.close() }
            runCatching { taskScope?.close() }
            throw error
        }
    }

    private fun unregisterLocked(context: Context) {
        context.closeInternal()
        contexts.remove(context.id, context)
        owners.remove(context.owner)
    }

    private inner class Context(
        val owner: Any,
        override val id: PluginId,
        override val tasks: TaskScope,
        override val events: EventScope,
        override val logger: PnLogger,
        override val metrics: MetricsController,
        override val diagnostics: DiagnosticRegistration?,
        override val updates: UpdateRegistration?,
    ) : PluginContext {
        private val contextClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = contextClosed.get()

        override fun messageBox(title: String): MessageBox = logging.box(owner, title)

        override fun shutdownBox(title: String): MessageBox = logging.shutdownBox(owner, title)

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
            runCatching { tasks.close() }
        }
    }

    private class Builder : PluginBuilder {
        var metricsProjectId: Int? = null
        var metricsEnabled: Boolean = false
        val metricsConfigurers = mutableListOf<Consumer<PluginMetrics>>()
        var diagnosticsDirectory: Path? = null
        var diagnosticContainer: DiagnosticContainer? = null
        var updateRequest: PluginUpdateRequest? = null
        val listeners = mutableListOf<Listener>()

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
}
