package ru.privatenull.pnlibrary.core.observability.report

import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.core.diagnostics.ConfigReader
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.observability.ObservabilityReportSnapshot
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Path
import java.time.Instant

/** Collects runtime information and assembles the plaintext support ZIP. */
internal class SupportReportAssembler(
    private val dataFolder: Path,
    private val config: PnLibraryConfig,
    private val diagnostics: DiagnosticsRegistry,
    private val platform: PlatformAdapter,
    private val diagnosticLogs: () -> List<Map<String, Any?>>,
    private val diagnosticHistory: () -> List<Pair<String, ByteArray>>,
    private val observabilitySnapshot: () -> ObservabilityReportSnapshot,
) {
    private val system = SystemReportCollector()
    private val configurations = ConfigReader(dataFolder, config)

    fun assemble(request: DebugRequest, encrypted: Boolean): ByteArray {
        val archive = SupportArchiveBuilder(config.maxReportBytes.toLong())
        addEnvironment(archive, request, encrypted)
        addPluginDiagnostics(archive, request.target)

        if (request.logs && config.logs) {
            addObservabilityData(archive)
            addDiagnosticLogs(archive)
            addDiagnosticHistory(archive)
        }
        if (request.configs && config.configs) {
            addConfigurations(archive, request.target, encrypted)
        }

        return archive.build().also { bytes ->
            require(bytes.size <= config.maxReportBytes) {
                "Diagnostic archive exceeds configured limit " +
                    "(${bytes.size} > ${config.maxReportBytes} bytes)"
            }
        }
    }

    private fun addEnvironment(
        archive: SupportArchiveBuilder,
        request: DebugRequest,
        encrypted: Boolean,
    ) {
        archive.json(
            "manifest.json",
            linkedMapOf(
                "schemaVersion" to 3,
                "producer" to "pnLibrary",
                "generatedUtc" to Instant.now().toString(),
                "target" to request.target,
                "platform" to platform.id,
                "platformImplementation" to platform.implementationName,
                "encrypted" to encrypted,
            ),
        )
        archive.json("system.json", system.collect(includeNetworkAddresses = encrypted))
        archive.text("threads.txt", system.threadDump())
        archive.json("platform.json", platform.diagnosticDetails(includeSensitive = encrypted))
    }

    private fun addPluginDiagnostics(archive: SupportArchiveBuilder, target: String) {
        diagnostics.snapshot(target).forEach { (plugin, snapshot) ->
            archive.json("plugins/${safePath(plugin)}/diagnostics.json", snapshot)
        }
    }

    private fun addObservabilityData(archive: SupportArchiveBuilder) {
        val snapshot = observabilitySnapshot()
        if (snapshot.journal.isNotEmpty()) {
            archive.bytes("observability/observations.jsonl", snapshot.journal)
        }
        if (snapshot.attachmentManifest.isNotEmpty()) {
            archive.bytes("observability/attachments.json", snapshot.attachmentManifest)
        }
        snapshot.attachments.forEach { (name, bytes) ->
            archive.bytes("observability/attachments/$name", bytes)
        }
    }

    private fun addDiagnosticLogs(archive: SupportArchiveBuilder) {
        diagnosticLogs()
            .takeLast(config.logRecords.coerceIn(1, 2_000))
            .groupBy { log -> safePath(log["plugin"]?.toString() ?: "runtime") }
            .forEach { (plugin, logs) ->
                archive.json("plugins/$plugin/logs/incidents.json", logs)
            }
    }

    private fun addDiagnosticHistory(archive: SupportArchiveBuilder) {
        diagnosticHistory().forEach { (name, content) ->
            archive.bytes("history/${safePath(name)}", content)
        }
    }

    private fun addConfigurations(
        archive: SupportArchiveBuilder,
        target: String,
        encrypted: Boolean,
    ) {
        diagnostics.configurations(target).forEach { registered ->
            val root = registered.dataDirectory ?: dataFolder
            val directory = "plugins/${safePath(registered.plugin)}/configuration/"
            if (encrypted) {
                val file = configurations.readExactFile(registered.configuration, root)
                val path = safeConfigurationPath(file.path)
                if (file.error == null) {
                    archive.bytes(directory + path, requireNotNull(file.content))
                } else {
                    archive.text(directory + "$path.error.txt", file.error + "\n")
                }
            } else {
                val file = configurations.readRedactedFile(registered.configuration, root)
                val path = safeConfigurationPath(file.path)
                if (file.error == null) {
                    archive.text(directory + path, file.content)
                } else {
                    archive.text(directory + "$path.error.txt", file.error + "\n")
                }
            }
        }
    }

    private fun safePath(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "unknown" }
        .take(96)

    private fun safeConfigurationPath(value: String): String = value
        .replace('\\', '/')
        .split('/')
        .filter { component -> component.isNotBlank() && component != "." && component != ".." }
        .joinToString("/") { component ->
            component.replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .take(128)
                .ifBlank { "config" }
        }
        .ifBlank { "config.txt" }
}
