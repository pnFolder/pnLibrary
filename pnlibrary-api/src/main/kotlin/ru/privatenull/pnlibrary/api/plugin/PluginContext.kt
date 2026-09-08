package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.logging.MessageBox
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration

/** All pnLibrary capabilities and registrations belonging to one [id]. */
interface PluginContext : AutoCloseable {
    val id: PluginId
    val events: EventScope
    val tasks: TaskScope
    val logger: PnLogger
    val metrics: MetricsController
    val diagnostics: DiagnosticRegistration?
    val updates: UpdateRegistration?
    val isClosed: Boolean

    /** Creates a formatted startup/status summary bound to this plugin. */
    fun messageBox(title: String = id.value): MessageBox

    /** Creates a formatted shutdown summary bound to this plugin. */
    fun shutdownBox(title: String = id.value): MessageBox

    /** Releases every capability and removes this context from the registry. */
    override fun close()
}
