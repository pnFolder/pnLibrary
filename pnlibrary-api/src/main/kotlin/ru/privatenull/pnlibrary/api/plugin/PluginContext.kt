package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.config.ConfigScope
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.services.ServiceManager
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.actions.PlayerActionService

/** All pnLibrary capabilities and registrations belonging to one [id]. */
interface PluginContext : AutoCloseable {
    val id: PluginId
    val metadata: PluginMetadata
    val lifecycle: PluginLifecycle
    val messages: PluginMessages
    val events: EventScope
    val tasks: TaskScope
    val services: ServiceManager
    val logger: PnLogger
    val configs: ConfigScope
    val actions: PlayerActionService
    val metrics: MetricsController
    val diagnostics: DiagnosticRegistration?
    val updates: UpdateRegistration?
    val isClosed: Boolean

    /** Releases every capability and removes this context from the registry. */
    override fun close()
}
