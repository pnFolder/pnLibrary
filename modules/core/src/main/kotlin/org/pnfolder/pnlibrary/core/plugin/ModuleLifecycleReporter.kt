package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.logging.MessageBox
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginLifecycle
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderHub

/** Builds the console cards printed when a module is enabled and disabled. */
internal class ModuleLifecycleReporter(
    private val owner: Any,
    private val moduleId: ModuleId,
    override val metadata: PluginMetadata,
    private val resources: ModuleResources,
    private val logging: LoggingService,
    private val placeholders: PlaceholderHub,
    private val placeholderApiEnabled: Boolean,
    private val isClosed: () -> Boolean,
) : PluginLifecycle {
    override fun enabled(): MessageBox =
        logging.box(owner, metadata.name, metadata.version)
            .ok("Identifier", moduleId.value)
            .ok("Platform", ModuleRuntimeSummary.platform(metadata))
            .status("Metrics", ModuleRuntimeSummary.metrics(resources.metrics.providerConfigurations, resources.metrics.isEnabled))
            .status("Updates", ModuleRuntimeSummary.updates(resources.updates))
            .status("Diagnostics", if (resources.diagnostics == null) null else "enabled")
            .status(
                "PlaceholderAPI",
                ModuleRuntimeSummary.placeholderApi(
                    placeholderApiEnabled,
                    placeholders.get("placeholderapi")?.state?.name,
                ),
            )

    override fun disabled(): MessageBox =
        logging.shutdownBox(owner, metadata.name, metadata.version)
            .status("Resources", if (isClosed()) "released" else "close pending")
            .status(
                "Updates",
                if (resources.updates == null) null else if (isClosed()) "stopped" else "registered",
            )
            .status(
                "Metrics",
                if (resources.metrics.providerConfigurations.isEmpty()) null
                else if (isClosed()) "stopped"
                else ModuleRuntimeSummary.metrics(resources.metrics.providerConfigurations, resources.metrics.isEnabled),
            )

    private fun MessageBox.status(label: String, detail: String?): MessageBox =
        if (detail == null) skip(label, "not configured") else ok(label, detail)
}
