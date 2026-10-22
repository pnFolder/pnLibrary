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
    override fun enabled(): MessageBox = HidingLifecycleBox(
        logging.box(owner, metadata.name, metadata.version),
        listOf(
            ok(LifecycleRow.IDENTIFIER, "Identifier", moduleId.value),
            ok(LifecycleRow.PLATFORM, "Platform", ModuleRuntimeSummary.platform(metadata)),
            status(LifecycleRow.METRICS, "Metrics",
                ModuleRuntimeSummary.metrics(resources.metrics.providerConfigurations, resources.metrics.isEnabled)),
            status(LifecycleRow.UPDATES, "Updates", ModuleRuntimeSummary.updates(resources.updates)),
            status(LifecycleRow.DIAGNOSTICS, "Diagnostics", if (resources.diagnostics == null) null else "enabled"),
            status(LifecycleRow.PLACEHOLDER_API, "PlaceholderAPI", ModuleRuntimeSummary.placeholderApi(
                placeholderApiEnabled,
                placeholders.get("placeholderapi")?.state?.name,
            )),
        ),
    )

    override fun disabled(): MessageBox = HidingLifecycleBox(
        logging.shutdownBox(owner, metadata.name, metadata.version),
        listOf(
            status(LifecycleRow.RESOURCES, "Resources", if (isClosed()) "released" else "close pending"),
            status(LifecycleRow.UPDATES, "Updates",
                if (resources.updates == null) null else if (isClosed()) "stopped" else "registered"),
            status(LifecycleRow.METRICS, "Metrics",
                if (resources.metrics.providerConfigurations.isEmpty()) null
                else if (isClosed()) "stopped"
                else ModuleRuntimeSummary.metrics(resources.metrics.providerConfigurations, resources.metrics.isEnabled)),
        ),
    )

    private fun ok(row: LifecycleRow, label: String, detail: String) =
        HidingLifecycleBox.AutoRow(row, notConfigured = false) { it.ok(label, detail) }

    /** A configured feature is written with its state; an unconfigured one is a "not configured" skip row. */
    private fun status(row: LifecycleRow, label: String, detail: String?) =
        if (detail == null) HidingLifecycleBox.AutoRow(row, notConfigured = true) { it.skip(label, "not configured") }
        else HidingLifecycleBox.AutoRow(row, notConfigured = false) { it.ok(label, detail) }
}
