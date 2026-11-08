package org.pnfolder.pnlibrary.core.diagnostics

import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * Reads plugin configuration files for support reports, with the values each report must hide.
 *
 * File confinement is done by [SecureConfigurationFileReader], syntax by [ConfigurationDocumentCodec], and
 * the hiding rules by [ConfigRedaction], built from the global settings and the file's own declaration.
 *
 * @param dataFolder default trusted root for relative configuration paths
 * @param globalConfig process-wide exclusions and patterns
 */
internal class ConfigReader(
    private val dataFolder: Path,
    private val globalConfig: PnLibraryConfig = PnLibraryConfig(),
) {
    private val redactor = DiagnosticRedactor()
    private val secureFileReader = SecureConfigurationFileReader(MAX_FILE_SIZE_BYTES)
    private val documentCodec = ConfigurationDocumentCodec(redactor)

    /** Text configuration prepared for a plaintext local report. */
    data class RedactedFile(
        val path: String,
        val content: String,
        val error: String? = null,
    )

    /** Configuration bytes for an encrypted report. */
    data class ExactFile(
        val path: String,
        val content: ByteArray? = null,
        val error: String? = null,
    )

    /**
     * Reads a file for an encrypted report: the original bytes, comments included, unless the file holds a value
     * that was asked to be hidden; then it is rendered again with those values hidden.
     */
    fun readProtectedFile(
        configSpec: DiagnosticConfiguration,
        rootDirectory: Path = dataFolder,
    ): ExactFile {
        val path = configSpec.path
        val read = secureFileReader.read(rootDirectory, path)
        val content = read.content ?: return ExactFile(path, error = read.error)
        val redaction = redaction(configSpec, rootDirectory)
        if (!documentCodec.isStructured(path)) {
            // Values of a format that is not parsed cannot be hidden one by one, so the file is only sent when nothing has to be.
            return if (redaction.hasRequests) ExactFile(path, error = UNPARSABLE_WITH_RULES) else ExactFile(path, content)
        }
        val data = runCatchingNonFatal { documentCodec.parse(path, content) }.getOrNull()
        if (data == null) {
            // A file that cannot be parsed cannot have single values hidden, so it is only sent when nothing has to be.
            return if (redaction.hasRequests) ExactFile(path, error = UNPARSABLE_WITH_RULES) else ExactFile(path, content)
        }
        val hidden = redaction.requested(data)
        if (hidden == data) return ExactFile(path, content)
        return ExactFile(path, documentCodec.render(path, hidden).toByteArray(StandardCharsets.UTF_8))
    }

    /** Reads, validates and redacts a configuration while preserving its original file format. */
    fun readRedactedFile(
        configSpec: DiagnosticConfiguration,
        rootDirectory: Path = dataFolder,
    ): RedactedFile {
        val result = readAndRedact(configSpec, rootDirectory)
        val error = result["error"]?.toString()
        if (error != null) return RedactedFile(configSpec.path, "", error)
        @Suppress("UNCHECKED_CAST")
        val data = result["data"] as? Map<String, Any?> ?: emptyMap()
        return RedactedFile(configSpec.path, documentCodec.render(configSpec.path, data))
    }

    /**
     * Returns the redacted neutral tree used by report serialization.
     *
     * The result always contains `path`. On failure it additionally contains
     * `error`; on success it contains `data`.
     */
    fun readAndRedact(
        configSpec: DiagnosticConfiguration,
        rootDirectory: Path = dataFolder,
    ): Map<String, Any?> {
        val path = configSpec.path
        val result = linkedMapOf<String, Any?>("path" to path)
        val read = secureFileReader.read(rootDirectory, path)
        val content = read.content
        if (content == null) {
            result["error"] = read.error
            return result
        }
        val data = try {
            documentCodec.parse(path, content)
        } catch (e: Exception) {
            result["error"] = "Parsing failed: ${e.javaClass.simpleName} - ${e.message}"
            return result
        }
        result["data"] = redaction(configSpec, rootDirectory).redact(data)
        return result
    }

    private fun redaction(configSpec: DiagnosticConfiguration, rootDirectory: Path): ConfigRedaction = ConfigRedaction(
        excludedPaths = (administratorPaths("${rootDirectory.fileName}/${configSpec.path}") + configSpec.excludedPaths).toSet(),
        keyPatterns = ConfigRedaction.compile(globalConfig.secretKeyPatterns + configSpec.secretKeyPatterns),
        valuePatterns = ConfigRedaction.compile(globalConfig.redactValuePatterns + configSpec.valuePatterns),
        redactor = redactor,
    )

    /** An entry is either a path for every file, or `plugin/file.yml:path` for that one file only. */
    private fun administratorPaths(fileKey: String): List<String> = globalConfig.excludedPaths.mapNotNull { entry ->
        val colon = entry.indexOf(':')
        when {
            colon < 0 -> entry
            entry.substring(0, colon).trim().equals(fileKey, ignoreCase = true) -> entry.substring(colon + 1).trim()
            else -> null
        }
    }

    companion object {
        const val MAX_FILE_SIZE_BYTES = 1_048_576L
        private const val UNPARSABLE_WITH_RULES = "[file cannot be parsed and has values that must stay hidden]"
    }
}
