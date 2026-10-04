package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.config.ConfigScope
import ru.privatenull.pnlibrary.api.cooldowns.CooldownService
import ru.privatenull.pnlibrary.api.currency.CurrencyService
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.plugin.MetricsController
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.text.ComponentService
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl

internal class ModuleResources(
    val tasks: TaskScope,
    val events: EventScope,
    val services: ServiceManagerImpl.OwnedServices,
    val logger: PnLogger,
    val configs: ConfigScope,
    val placeholders: PlaceholderService,
    val components: ComponentService,
    val cooldowns: CooldownService,
    val currency: CurrencyService,
    val metrics: MetricsController,
    val errors: ErrorReporter,
    val diagnostics: DiagnosticRegistration?,
    val updates: UpdateRegistration?,
    val downloads: DownloadRegistration?,
) {
    fun close(diagnosticsService: DiagnosticsService, moduleKey: PluginId) {
        ResourceCleanup.closeAll(
            { downloads?.close() },
            { updates?.close() },
            { diagnostics?.close() },
            { diagnosticsService.clearPlugin(moduleKey.value) },
            { metrics.close() },
            { errors.close() },
            { currency.close() },
            { cooldowns.close() },
            { placeholders.close() },
            { configs.close() },
            { services.close() },
            { events.close() },
            { tasks.close() },
        )
    }
}
