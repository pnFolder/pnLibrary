package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.currency.CurrencyService
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageFactory
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.events.EventScope
import org.pnfolder.pnlibrary.api.events.EventService
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.metrics.ErrorReporter
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.metrics.TelemetryError
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.config.ConfigurationRuntime
import org.pnfolder.pnlibrary.core.cooldowns.CooldownServiceImpl
import org.pnfolder.pnlibrary.core.downloads.CompositeDownloadRegistration
import org.pnfolder.pnlibrary.core.downloads.DirectDownloadManager
import org.pnfolder.pnlibrary.core.lifecycle.ResourceCleanup
import org.pnfolder.pnlibrary.core.metrics.ErrorPipeline
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderHub
import org.pnfolder.pnlibrary.core.plugin.MetricsControllerImpl
import org.pnfolder.pnlibrary.core.services.ServiceManagerImpl
import org.pnfolder.pnlibrary.core.tasks.TaskService
import org.pnfolder.pnlibrary.core.text.ComponentCache
import org.pnfolder.pnlibrary.core.text.ComponentServiceImpl
import org.pnfolder.pnlibrary.currency.CurrencyRuntime

/** Opens every per-module resource and releases the ones already opened when a later one fails. */
internal class ModuleResourceFactory(
    private val events: EventService,
    private val tasks: TaskService,
    private val services: ServiceManagerImpl,
    private val logging: LoggingService,
    private val metrics: MetricsService,
    private val diagnostics: DiagnosticsService,
    private val updates: UpdateService,
    private val placeholders: PlaceholderHub,
    private val currency: CurrencyRuntime,
    private val configurations: ConfigurationRuntime,
    private val directDownloads: DirectDownloadManager?,
    private val componentCache: ComponentCache,
) {
    fun create(
        owner: Any,
        moduleId: ModuleId,
        moduleKey: PluginId,
        pluginId: PluginId,
        definition: ModuleDefinitionBuilder,
        product: ProductDescriptor?,
    ): ModuleResources {
        var taskScope: TaskScope? = null
        var eventScope: EventScope? = null
        var configScope: ConfigScope? = null
        var placeholderScope: PlaceholderService? = null
        var currencyScope: CurrencyService? = null
        var metricsController: MetricsControllerImpl? = null
        var errorReporter: ErrorReporter? = null
        var diagnosticRegistration: DiagnosticRegistration? = null
        var updateRegistration: UpdateRegistration? = null
        var downloadRegistration: DownloadRegistration? = null
        val serviceScope = services.ownedBy(moduleKey)

        try {
            taskScope = tasks.scope(owner, moduleKey)
            eventScope = events.scope(moduleKey, pluginId, moduleId)
            configScope = configurations.scope(owner, moduleKey)
            placeholderScope = placeholders.scope(moduleKey, definition.placeholderApiEnabled)
            currencyScope = currency.scope(moduleKey, placeholderScope)

            serviceScope.register(CurrencyService::class.java, currencyScope)
            serviceScope.register(CurrencyStorageFactory::class.java, currency.storages)

            // Resolve the live session on every capture, including after disable/restart.
            errorReporter = ErrorPipeline(object : ErrorReporter {
                override fun capture(error: TelemetryError) {
                    metricsController?.sessionErrorReporter()?.capture(error)
                }

                override fun close() = Unit
            })
            metricsController = MetricsControllerImpl(
                owner,
                metrics,
                definition.metrics,
                errorReporter,
            )
            diagnosticRegistration = definition.diagnosticContainer?.let { container ->
                diagnostics.register(moduleKey.value, requireNotNull(definition.diagnosticsDirectory), container)
            }
            updateRegistration = definition.updateRequest?.let { request ->
                updates.register(owner, requireNotNull(product), request, definition.dependencies)
            }

            // Retain the first registration immediately so a later failure can roll it back.
            downloadRegistration = directDownloads?.registerDependencies(owner, definition.dependencies)
            val fileDownloads = definition.downloadsRequest?.let { directDownloads?.register(owner, it) }
            downloadRegistration = CompositeDownloadRegistration.combine(downloadRegistration, fileDownloads)

            return ModuleResources(
                tasks = taskScope,
                events = eventScope,
                services = serviceScope,
                logger = logging.logger(owner, moduleId.value),
                configs = configScope,
                placeholders = placeholderScope,
                components = ComponentServiceImpl(placeholderScope, componentCache),
                cooldowns = CooldownServiceImpl(),
                currency = currencyScope,
                metrics = metricsController,
                errors = errorReporter,
                diagnostics = diagnosticRegistration,
                updates = updateRegistration,
                downloads = downloadRegistration,
            )
        } catch (error: Throwable) {
            ResourceCleanup.suppressInto(
                error,
                { downloadRegistration?.close() },
                { updateRegistration?.close() },
                { diagnosticRegistration?.close() },
                { metricsController?.close() },
                { errorReporter?.close() },
                { currencyScope?.close() },
                { placeholderScope?.close() },
                { configScope?.close() },
                { eventScope?.close() },
                { serviceScope.close() },
                { taskScope?.close() },
            )
            throw error
        }
    }
}
