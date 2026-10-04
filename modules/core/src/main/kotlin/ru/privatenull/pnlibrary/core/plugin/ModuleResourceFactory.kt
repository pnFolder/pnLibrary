package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.config.ConfigScope
import ru.privatenull.pnlibrary.api.currency.CurrencyService
import ru.privatenull.pnlibrary.api.currency.CurrencyStorageFactory
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.events.EventService
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.tasks.TaskService
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.updates.UpdateService
import ru.privatenull.pnlibrary.core.config.ConfigurationServiceImpl
import ru.privatenull.pnlibrary.core.cooldowns.CooldownServiceImpl
import ru.privatenull.pnlibrary.core.downloads.CompositeDownloadRegistration
import ru.privatenull.pnlibrary.core.downloads.DirectDownloadManager
import ru.privatenull.pnlibrary.core.placeholders.PlaceholderHub
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl
import ru.privatenull.pnlibrary.core.tasks.TaskServiceImpl
import ru.privatenull.pnlibrary.core.text.ComponentCache
import ru.privatenull.pnlibrary.core.text.ComponentServiceImpl
import ru.privatenull.pnlibrary.currency.CurrencyFeature
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter

internal class ModuleResourceFactory(
    private val platform: PlatformAdapter,
    private val events: EventService,
    private val tasks: TaskService,
    private val services: ServiceManagerImpl,
    private val logging: LoggingService,
    private val metrics: MetricsService,
    private val diagnostics: DiagnosticsService,
    private val updates: UpdateService,
    private val placeholders: PlaceholderHub,
    private val currency: CurrencyFeature,
    private val configurations: ConfigurationServiceImpl,
    private val directDownloads: DirectDownloadManager?,
    private val componentCache: ComponentCache,
) {
    fun create(
        owner: Any,
        moduleId: ModuleId,
        moduleKey: PluginId,
        definition: ModuleDefinitionBuilder,
        product: ProductDescriptor?,
    ): ModuleResources {
        var taskScope: TaskScope? = null
        var eventScope: EventScope? = null
        var configScope: ConfigScope? = null
        var placeholderScope: PlaceholderService? = null
        var currencyScope: CurrencyService? = null
        var metricsController: MetricsControllerImpl? = null
        var diagnosticRegistration: DiagnosticRegistration? = null
        var updateRegistration: UpdateRegistration? = null
        var downloadRegistration: DownloadRegistration? = null
        val serviceScope = services.ownedBy(moduleKey)

        try {
            taskScope = if (tasks is TaskServiceImpl) tasks.scope(owner, moduleKey) else tasks.scope(owner)
            eventScope = events.scope(moduleKey)
            configScope = configurations.scope(owner, moduleKey)
            placeholderScope = placeholders.scope(moduleKey, definition.placeholderApiEnabled)
            currencyScope = currency.scope(moduleKey, placeholderScope)

            serviceScope.register(CurrencyService::class.java, currencyScope)
            serviceScope.register(CurrencyStorageFactory::class.java, currency.storages)
            definition.listeners.forEach(eventScope::register)

            metricsController = MetricsControllerImpl(
                owner, metrics, definition.metricsProjectId, definition.metricsEnabled, definition.metricsConfigurers,
            )
            diagnosticRegistration = definition.diagnosticContainer?.let { container ->
                diagnostics.register(moduleKey.value, requireNotNull(definition.diagnosticsDirectory), container)
            }
            updateRegistration = definition.updateRequest?.let { request ->
                updates.register(owner, requireNotNull(product), request, definition.dependencies)
            }

            val dependencyDownloads = directDownloads?.registerDependencies(owner, definition.dependencies)
            val fileDownloads = definition.downloadsRequest?.let { directDownloads?.register(owner, it) }
            downloadRegistration = CompositeDownloadRegistration.combine(dependencyDownloads, fileDownloads)

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
                { currencyScope?.close() },
                { placeholderScope?.close() },
                { configScope?.close() },
                { eventScope?.close() },
                { services.unregisterAll(moduleKey) },
                { taskScope?.close() },
            )
            throw error
        }
    }
}
