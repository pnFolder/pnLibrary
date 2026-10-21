package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.cooldowns.CooldownService
import org.pnfolder.pnlibrary.api.currency.CurrencyService
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.events.EventScope
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.metrics.ErrorReporter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.metrics.MetricsController
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.text.ComponentService
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.core.lifecycle.ResourceCleanup
import org.pnfolder.pnlibrary.core.services.ServiceManagerImpl

/** Resources owned by one module, closed together in dependency order. */
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
