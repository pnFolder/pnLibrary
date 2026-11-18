package org.pnfolder.pnlibrary.core.observability

import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticLevel
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticRegistration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsContributor
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService
import org.pnfolder.pnlibrary.api.observability.ComponentStatus
import org.pnfolder.pnlibrary.api.observability.ObservabilityReport
import org.pnfolder.pnlibrary.api.observability.ObservabilityReportRequest
import org.pnfolder.pnlibrary.api.observability.ObservabilityService
import org.pnfolder.pnlibrary.api.observability.Observation
import org.pnfolder.pnlibrary.api.observability.ObservationQuery
import org.pnfolder.pnlibrary.api.observability.ObservationRequest
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import java.nio.file.Path

// PnLibrary.diagnostics and PnLibrary.observability are this one object seen through two interfaces.
// Storage and retention stay inside ObservabilityRuntime.
internal class ObservabilityHub(
    private val diagnostics: DiagnosticsRegistry,
    private val runtime: ObservabilityRuntime,
) : DiagnosticsService, ObservabilityService {
    override val apiVersion: Int
        get() = diagnostics.apiVersion

    override fun register(
        plugin: String,
        contributor: DiagnosticsContributor,
    ): DiagnosticRegistration = diagnostics.register(plugin, contributor)

    override fun register(
        plugin: String,
        dataDirectory: Path,
        contributor: DiagnosticsContributor,
    ): DiagnosticRegistration = diagnostics.register(plugin, dataDirectory, contributor)

    override fun status(
        plugin: String,
        component: String,
        state: String,
        detail: String,
        fields: Map<String, Any?>,
    ) {
        diagnostics.status(plugin, component, state, detail, fields)
        runtime.status(
            ComponentStatus(
                plugin = plugin,
                component = component,
                state = state,
                detail = detail,
                data = fields.toStringValues(),
            ),
        )
    }

    override fun clearStatus(plugin: String, component: String) {
        diagnostics.clearStatus(plugin, component)
    }

    override fun clearPlugin(plugin: String) {
        diagnostics.clearPlugin(plugin)
    }

    override fun record(
        plugin: String,
        level: DiagnosticLevel,
        component: String,
        code: String,
        message: String,
        error: Throwable?,
        fields: Map<String, Any?>,
    ) {
        diagnostics.record(plugin, level, component, code, message, error, fields)
    }

    override fun record(request: ObservationRequest): Observation = runtime.record(request)

    override fun recent(query: ObservationQuery): List<Observation> = runtime.recent(query)

    override fun status(status: ComponentStatus): ComponentStatus = runtime.status(status)

    override fun createReport(request: ObservabilityReportRequest): ObservabilityReport =
        runtime.createReport(request)

    override fun close() {
        runtime.close()
    }
}
