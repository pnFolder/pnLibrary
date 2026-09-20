package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.actions.ActionService
import ru.privatenull.pnlibrary.api.config.ConfigScope
import ru.privatenull.pnlibrary.api.cooldowns.CooldownService
import ru.privatenull.pnlibrary.api.currency.CurrencyService
import ru.privatenull.pnlibrary.api.currency.CurrencyStorageFactory
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.services.ServiceManager
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.text.ComponentService
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration

/**
 * Lifecycle owner for every pnLibrary capability registered by one plugin.
 *
 * A context is created atomically by [PluginRegistry.register]. If setup fails, pnLibrary releases
 * capabilities already created during that attempt. Retain the context and close it from the native
 * plugin's disable hook; closing it cancels tasks, listeners, services, configuration handles,
 * placeholders, currencies, metrics, diagnostics, and update registrations owned by this plugin.
 */
interface PluginContext : AutoCloseable {
    /** Stable normalized identity used by cross-plugin pnLibrary registries. */
    val id: PluginId
    /** Immutable metadata captured when this context was registered. */
    val metadata: PluginMetadata
    /** Factory for consistent buffered enable and disable summaries. */
    val lifecycle: PluginLifecycle
    /** Factory for plugin-bound buffered console message boxes. */
    val messages: PluginMessages
    /** Event scope whose subscriptions are removed with this context. */
    val events: EventScope
    /** Task scope whose scheduled work is cancelled with this context. */
    val tasks: TaskScope
    /** Typed service view whose registrations belong to this plugin ID. */
    val services: ServiceManager
    /** Logger bound to the native owner and plugin identity. */
    val logger: PnLogger
    /** Typed configuration scope rooted in the native plugin data directory. */
    val configs: ConfigScope
    /** Declarative player and audience action executor. */
    val actions: ActionService
    /** Placeholder scope containing expansions owned by this plugin. */
    val placeholders: PlaceholderService
    /** Component serializer and placeholder-rendering facade. */
    val components: ComponentService
    /** In-memory cooldown service isolated to this context. */
    val cooldowns: CooldownService
    /** Currency registry and operations visible to this plugin. */
    val currencies: CurrencyService
    /** Factory for plugin-managed file and JDBC currency storage. */
    val currencyStorages: CurrencyStorageFactory
    /** Runtime controller for this plugin's optional metrics session. */
    val metrics: MetricsController
    /** Diagnostics registration created by the builder, or `null` when not configured. */
    val diagnostics: DiagnosticRegistration?
    /** Update registration created by the builder, or `null` when not configured. */
    val updates: UpdateRegistration?
    /** Direct component/plugin/file delivery registered by the builder, or `null`. */
    val downloads: DownloadRegistration?
    /** Whether this context has completed or begun its idempotent teardown. */
    val isClosed: Boolean

    /** Releases every owned capability and removes this context from its registry. */
    override fun close()
}
