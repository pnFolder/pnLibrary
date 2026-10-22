package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.logging.MessageBox
import org.pnfolder.pnlibrary.api.plugin.LifecycleRow
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
    override fun enabled(): MessageBox = enabled(*emptyArray<LifecycleRow>())

    override fun disabled(): MessageBox = disabled(*emptyArray<LifecycleRow>())

    override fun enabled(vararg hide: LifecycleRow): MessageBox {
        val hidden = hide.toSet()
        return logging.box(owner, metadata.name, metadata.version)
            .ok(hidden, LifecycleRow.IDENTIFIER, "Identifier", moduleId.value)
            .ok(hidden, LifecycleRow.PLATFORM, "Platform", ModuleRuntimeSummary.platform(metadata))
            .status(hidden, LifecycleRow.METRICS, "Metrics",
                ModuleRuntimeSummary.metrics(resources.metrics.providerConfigurations, resources.metrics.isEnabled))
            .status(hidden, LifecycleRow.UPDATES, "Updates", ModuleRuntimeSummary.updates(resources.updates))
            .status(hidden, LifecycleRow.DIAGNOSTICS, "Diagnostics", if (resources.diagnostics == null) null else "enabled")
            .status(
                hidden, LifecycleRow.PLACEHOLDER_API, "PlaceholderAPI",
                ModuleRuntimeSummary.placeholderApi(
                    placeholderApiEnabled,
                    placeholders.get("placeholderapi")?.state?.name,
                ),
            )
    }

    override fun disabled(vararg hide: LifecycleRow): MessageBox {
        val hidden = hide.toSet()
        return logging.shutdownBox(owner, metadata.name, metadata.version)
            .status(hidden, LifecycleRow.RESOURCES, "Resources", if (isClosed()) "released" else "close pending")
            .status(
                hidden, LifecycleRow.UPDATES, "Updates",
                if (resources.updates == null) null else if (isClosed()) "stopped" else "registered",
            )
            .status(
                hidden, LifecycleRow.METRICS, "Metrics",
                if (resources.metrics.providerConfigurations.isEmpty()) null
                else if (isClosed()) "stopped"
                else ModuleRuntimeSummary.metrics(resources.metrics.providerConfigurations, resources.metrics.isEnabled),
            )
    }

    private fun MessageBox.ok(hidden: Set<LifecycleRow>, row: LifecycleRow, label: String, detail: String): MessageBox =
        if (row in hidden) this else ok(label, detail)

    /** A configured feature is shown with its state; an unconfigured one reads "not configured" unless hidden. */
    private fun MessageBox.status(hidden: Set<LifecycleRow>, row: LifecycleRow, label: String, detail: String?): MessageBox = when {
        row in hidden -> this
        detail == null -> if (LifecycleRow.NOT_CONFIGURED in hidden) this else skip(label, "not configured")
        else -> ok(label, detail)
    }
}
