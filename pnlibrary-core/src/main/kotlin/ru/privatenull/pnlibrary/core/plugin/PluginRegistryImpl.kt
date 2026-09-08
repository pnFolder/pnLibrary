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
import ru.privatenull.pnlibrary.api.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.plugin.MetricsController
import ru.privatenull.pnlibrary.api.plugin.LifecycleReport
import ru.privatenull.pnlibrary.api.plugin.PluginBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginContext
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.plugin.PluginLifecycle
import ru.privatenull.pnlibrary.api.plugin.PluginLifecycleBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.plugin.PluginMetadataBuilder
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
    private val platform: PlatformAdapter,
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
                it.showEnabledMessage()
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
                metadata,
                taskScope,
                eventScope,
                logging.logger(owner, id.value),
                metricsController,
                diagnosticRegistration,
                updateRegistration,
                definition.listeners.size,
                definition.lifecycleMessages,
                definition.enabledReports.toList(),
                definition.disabledReports.toList(),
            )
        } catch (error: Throwable) {
            runCatching { updateRegistration?.close() }
            runCatching { diagnosticRegistration?.close() }
            runCatching { metricsController?.close() }
            runCatching { eventScope?.close() }
            runCatching { taskScope?.close() }
            if (definition.lifecycleMessages) {
                runCatching {
                    logging.box(owner, metadata.name, metadata.version)
                        .fail("Registration", error.message ?: error.javaClass.simpleName, error)
                        .show()
                }
            }
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
        override val logger: PnLogger,
        override val metrics: MetricsController,
        override val diagnostics: DiagnosticRegistration?,
        override val updates: UpdateRegistration?,
        private val listenerCount: Int,
        private val lifecycleMessages: Boolean,
        private val enabledReports: List<Consumer<LifecycleReport>>,
        private val disabledReports: List<Consumer<LifecycleReport>>,
    ) : PluginContext {
        private val contextClosed = AtomicBoolean(false)
        override val isClosed: Boolean get() = contextClosed.get()
        override val lifecycle: PluginLifecycle = object : PluginLifecycle {
            override val metadata: PluginMetadata get() = this@Context.metadata
            override fun enabled(): MessageBox =
                logging.box(owner, metadata.name, metadata.version)
            override fun disabled(): MessageBox =
                logging.shutdownBox(owner, metadata.name, metadata.version)
        }

        fun showEnabledMessage() {
            if (!lifecycleMessages) return
            runCatching {
                val box = lifecycle.enabled()
                    .ok("Identifier", id.value)
                    .ok("Platform", platformSummary())
                    .status("Metrics", metricsStatus())
                    .status("Updates", updatesStatus())
                    .status("Diagnostics", if (diagnostics == null) null else "enabled")
                    .status("Events", if (listenerCount == 0) null else "$listenerCount listener(s)")
                val report = LifecycleReportImpl(box)
                enabledReports.forEach { it.accept(report) }
                box.show()
            }.onFailure { logger.warning("Unable to display startup summary: ${it.message}") }
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
            runCatching { tasks.close() }
            if (lifecycleMessages) {
                runCatching {
                    val box = lifecycle.disabled()
                        .ok("Resources", "released")
                        .status("Updates", if (updates == null) null else "stopped")
                        .status("Metrics", if (metrics.projectId == null) null else "stopped")
                        .status("Events", if (listenerCount == 0) null else "$listenerCount listener(s) removed")
                    val report = LifecycleReportImpl(box)
                    disabledReports.forEach { it.accept(report) }
                    box.show()
                }
            }
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
    }

    private class Builder : PluginBuilder {
        var lifecycleMessages: Boolean = true
        var metadataName: String? = null
        var metadataVersion: String? = null
        var metadataAuthors: String? = null
        val enabledReports = mutableListOf<Consumer<LifecycleReport>>()
        val disabledReports = mutableListOf<Consumer<LifecycleReport>>()
        var metricsProjectId: Int? = null
        var metricsEnabled: Boolean = false
        val metricsConfigurers = mutableListOf<Consumer<PluginMetrics>>()
        var diagnosticsDirectory: Path? = null
        var diagnosticContainer: DiagnosticContainer? = null
        var updateRequest: PluginUpdateRequest? = null
        val listeners = mutableListOf<Listener>()

        override fun lifecycleMessages(enabled: Boolean): PluginBuilder = apply {
            lifecycleMessages = enabled
        }

        override fun metadata(configure: Consumer<PluginMetadataBuilder>): PluginBuilder = apply {
            configure.accept(MetadataBuilder(this))
        }

        override fun lifecycle(configure: Consumer<PluginLifecycleBuilder>): PluginBuilder = apply {
            configure.accept(LifecycleBuilder(this))
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

    private class LifecycleBuilder(private val target: Builder) : PluginLifecycleBuilder {
        override fun enabled(configure: Consumer<LifecycleReport>): PluginLifecycleBuilder = apply {
            target.enabledReports += configure
        }
        override fun disabled(configure: Consumer<LifecycleReport>): PluginLifecycleBuilder = apply {
            target.disabledReports += configure
        }
    }

    private class LifecycleReportImpl(private val box: MessageBox) : LifecycleReport {
        override fun ok(label: String, detail: String): LifecycleReport = apply { box.ok(label, detail) }
        override fun warn(label: String, detail: String): LifecycleReport = apply { box.warn(label, detail) }
        override fun skip(label: String, detail: String): LifecycleReport = apply { box.skip(label, detail) }
        override fun fail(label: String, detail: String, error: Throwable?): LifecycleReport =
            apply { box.fail(label, detail, error) }
    }
}
