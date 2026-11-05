package org.pnfolder.pnlibrary.core.diagnostics

import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsContributor
import org.pnfolder.pnlibrary.config.ManagedConfigFile

/**
 * Puts every file a plugin registers through the configuration service into that plugin's report, with the fields
 * marked `@ConfigSecret` hidden. The returned handle removes the file from the report again.
 */
internal fun configFileDiagnostics(diagnostics: DiagnosticsRegistry): (ManagedConfigFile) -> AutoCloseable? = { file ->
    val configuration = DiagnosticConfiguration.builder(file.path).also { builder ->
        file.secretPaths.forEach(builder::exclude)
    }.build()
    val contributor = object : DiagnosticsContributor {
        override val id: String = "config:${file.path}"
        override fun collect(): Map<String, Any?> = emptyMap()
        override fun configurations(): Collection<DiagnosticConfiguration> = listOf(configuration)
    }
    diagnostics.register(file.pluginId.value, file.directory, contributor)
}
