package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration

/** All pnLibrary capabilities and registrations belonging to one [id]. */
interface PluginContext : AutoCloseable {
    val id: PluginId
    val metadata: PluginMetadata
    val lifecycle: PluginLifecycle
    val messages: PluginMessages
    val events: EventScope
    val tasks: TaskScope
    val logger: PnLogger
    val metrics: MetricsController
    val diagnostics: DiagnosticRegistration?
    val updates: UpdateRegistration?
    val isClosed: Boolean

    /** Registers a typed service owned by this plugin and removed on [close]. */
    fun <T : Any> registerService(
        type: Class<T>,
        service: T,
        priority: Int = 0,
    )

    /** Removes the service of [type] registered by this plugin, if present. */
    fun <T : Any> unregisterService(type: Class<T>)

    /** Releases every capability and removes this context from the registry. */
    override fun close()
}
