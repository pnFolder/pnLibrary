package ru.privatenull.pnlibrary.core.diagnostics

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticConfiguration
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * Orchestrates secure configuration collection and layered secret redaction.
 *
 * File confinement is delegated to [SecureConfigurationFileReader] and document
 * syntax to [ConfigurationDocumentCodec]. This class applies the global and
 * contributor-specific exclusion policies to the resulting neutral data tree.
 *
 * @param dataFolder default trusted root for relative configuration paths
 * @param globalConfig process-wide limits and redaction expressions
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

    /** Original configuration bytes permitted only inside an encrypted report. */
    data class ExactFile(
        val path: String,
        val content: ByteArray? = null,
        val error: String? = null,
    )

    /** Reads the original bytes after the same path, symlink, type and size checks. */
    fun readExactFile(
        configSpec: DiagnosticConfiguration,
        rootDirectory: Path = dataFolder,
    ): ExactFile {
        val relative = configSpec.path
        val result = secureFileReader.read(rootDirectory, relative)
        return ExactFile(relative, content = result.content, error = result.error)
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
        val relPath = configSpec.path
        val result = linkedMapOf<String, Any?>()
        result["path"] = relPath

        val read = secureFileReader.read(rootDirectory, relPath)
        if (read.error != null) {
            result["error"] = read.error
            return result
        }

        val rawMap = try {
            documentCodec.parse(relPath, requireNotNull(read.content))
        } catch (e: Exception) {
            result["error"] = "Parsing failed: ${e.javaClass.simpleName} - ${e.message}"
            return result
        }

        // ── Multi-Layer Redaction ──────────────────────────────────────────────
        val keyRegexes = compileRegexes(
            globalConfig.secretKeyPatterns + configSpec.secretKeyPatterns
        )
        val valueRegexes = compileRegexes(
            globalConfig.redactValuePatterns + configSpec.valuePatterns
        )
        val excludedSubtrees = (globalConfig.excludedPaths + configSpec.excludedPaths).toSet()

        val redactedData = redactTree(
            data = rawMap,
            currentPath = "",
            excludedSubtrees = excludedSubtrees,
            keyRegexes = keyRegexes,
            valueRegexes = valueRegexes,
            depth = 0
        )

        result["data"] = redactedData
        return result
    }

    private fun redactTree(
        data: Map<String, Any?>,
        currentPath: String,
        excludedSubtrees: Set<String>,
        keyRegexes: List<Pattern>,
        valueRegexes: List<Pattern>,
        depth: Int,
    ): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        if (depth >= MAX_DEPTH) {
            result["[limit]"] = "[depth limit reached]"
            return result
        }

        var entryCount = 0
        for ((key, value) in data) {
            if (entryCount++ >= MAX_ENTRIES_PER_MAP) break
            val fullPath = if (currentPath.isEmpty()) key else "$currentPath.$key"

            // Layer 1: Subtree exclusion
            if (excludedSubtrees.contains(fullPath)) {
                result[key] = "[EXCLUDED BY RULE]"
                continue
            }

            // Layer 2: Built-in & Custom Secret Key regex matching
            if (isSecretKey(key, fullPath, keyRegexes)) {
                result[key] = "[REDACTED SECRET KEY]"
                continue
            }

            // Layer 3: Nested object / map
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                result[key] = redactTree(
                    data = value as Map<String, Any?>,
                    currentPath = fullPath,
                    excludedSubtrees = excludedSubtrees,
                    keyRegexes = keyRegexes,
                    valueRegexes = valueRegexes,
                    depth = depth + 1
                )
            } else if (value is List<*>) {
                result[key] = value.take(MAX_ENTRIES_PER_LIST).map { item ->
                    if (item is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        redactTree(
                            data = item as Map<String, Any?>,
                            currentPath = fullPath,
                            excludedSubtrees = excludedSubtrees,
                            keyRegexes = keyRegexes,
                            valueRegexes = valueRegexes,
                            depth = depth + 1
                        )
                    } else when (item) {
                        null, is Number, is Boolean -> item
                        else -> redactStringValue(item.toString(), valueRegexes)
                    }
                }
            } else {
                result[key] = when (value) {
                    null, is Number, is Boolean -> value
                    else -> redactStringValue(value.toString(), valueRegexes)
                }
            }
        }
        return result
    }

    private fun redactStringValue(input: String, valueRegexes: List<Pattern>): String {
        var str = redactor.redact(input)
        for (pattern in valueRegexes) {
            try {
                str = pattern.matcher(str).replaceAll("[REDACTED VALUE]")
            } catch (_: Exception) {
                // Safeguard against expensive/broken regexes
            }
        }
        return str
    }

    private fun isSecretKey(key: String, fullPath: String, keyRegexes: List<Pattern>): Boolean {
        if (BUILTIN_SECRET_KEY.matcher(key).matches() || BUILTIN_SECRET_KEY.matcher(fullPath).matches()) {
            return true
        }
        for (pattern in keyRegexes) {
            try {
                if (pattern.matcher(key).find() || pattern.matcher(fullPath).find()) {
                    return true
                }
            } catch (_: Exception) { }
        }
        return false
    }

    private fun compileRegexes(patterns: List<String>): List<Pattern> {
        val result = mutableListOf<Pattern>()
        for (pat in patterns) {
            if (pat.isNotBlank() && pat.length <= 256) {
                try {
                    result.add(Pattern.compile(pat, Pattern.CASE_INSENSITIVE))
                } catch (_: Exception) { }
            }
        }
        return result
    }

    companion object {
        const val MAX_FILE_SIZE_BYTES = 1_048_576L // 1 MiB
        const val MAX_DEPTH = 10
        const val MAX_ENTRIES_PER_MAP = 200
        const val MAX_ENTRIES_PER_LIST = 100

        private val BUILTIN_SECRET_KEY = Pattern.compile(
            "(?i).*(?:password|passwd|pwd|secret|token|api[-_ ]?key|authorization|cookie|private[-_ ]?key|credential|webhook|mysql|auth|jdbc).*"
        )

    }
}
