package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.logging.MessageBox
import ru.privatenull.pnlibrary.api.plugin.ModuleId
import ru.privatenull.pnlibrary.api.plugin.PluginLifecycle
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.core.placeholders.PlaceholderHub

internal class ModuleLifecycleReporter(
    private val owner: Any,
    private val moduleId: ModuleId,
    override val metadata: PluginMetadata,
    private val resources: ModuleResources,
    private val logging: LoggingService,
    private val placeholders: PlaceholderHub,
    private val placeholderApiEnabled: Boolean,
    private val listenerCount: Int,
    private val isClosed: () -> Boolean,
) : PluginLifecycle {
    override fun enabled(): MessageBox =
        logging.box(owner, metadata.name, metadata.version)
            .ok("Identifier", moduleId.value)
            .ok("Platform", ModuleRuntimeSummary.platform(metadata))
            .status("Metrics", ModuleRuntimeSummary.metrics(resources.metrics.projectId, resources.metrics.isEnabled))
            .status("Updates", ModuleRuntimeSummary.updates(resources.updates))
            .status("Diagnostics", if (resources.diagnostics == null) null else "enabled")
            .status(
                "PlaceholderAPI",
                ModuleRuntimeSummary.placeholderApi(
                    placeholderApiEnabled,
                    placeholders.get("placeholderapi")?.state?.name,
                ),
            )
            .status("Events", if (listenerCount == 0) null else "$listenerCount listener(s)")

    override fun disabled(): MessageBox =
        logging.shutdownBox(owner, metadata.name, metadata.version)
            .status("Resources", if (isClosed()) "released" else "close pending")
            .status(
                "Updates",
                if (resources.updates == null) null else if (isClosed()) "stopped" else "registered",
            )
            .status(
                "Metrics",
                if (resources.metrics.projectId == null) null
                else if (isClosed()) "stopped"
                else ModuleRuntimeSummary.metrics(resources.metrics.projectId, resources.metrics.isEnabled),
            )
            .status(
                "Events",
                if (listenerCount == 0) null
                else if (isClosed()) "$listenerCount listener(s) removed"
                else "$listenerCount listener(s)",
            )

    private fun MessageBox.status(label: String, detail: String?): MessageBox =
        if (detail == null) skip(label, "not configured") else ok(label, detail)
}
