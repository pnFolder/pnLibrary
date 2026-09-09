package ru.privatenull.pnlibrary.core.diagnostics

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticConfiguration
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.regex.Pattern

/**
 * Secure, multi-format configuration reader with layered secret redaction.
 */
class ConfigReader(
    private val dataFolder: Path,
    private val globalConfig: PnLibraryConfig = PnLibraryConfig(),
) {

    private val redactor = DiagnosticRedactor()

    data class RedactedFile(val path: String, val content: String, val error: String? = null)
    data class ExactFile(val path: String, val content: ByteArray? = null, val error: String? = null)

    /** Reads the original bytes after the same path, symlink, type and size checks. */
    fun readExactFile(
        configSpec: DiagnosticConfiguration,
        rootDirectory: Path = dataFolder,
    ): ExactFile {
        val relative = configSpec.path
        val root = rootDirectory.toAbsolutePath().normalize()
        val target = root.resolve(relative).normalize()
        if (!target.startsWith(root)) return ExactFile(relative, error = "[SECURITY: path traversal blocked]")
        if (Files.isSymbolicLink(target)) return ExactFile(relative, error = "[SECURITY: symlink escape blocked]")
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return ExactFile(relative, error = "[file not found or not a regular file]")
        }
        val realRoot = runCatching { root.toRealPath() }.getOrNull()
            ?: return ExactFile(relative, error = "[SECURITY: configuration root cannot be resolved]")
        val realTarget = runCatching { target.toRealPath() }.getOrNull()
            ?: return ExactFile(relative, error = "[SECURITY: configuration path cannot be resolved]")
        if (!realTarget.startsWith(realRoot)) return ExactFile(relative, error = "[SECURITY: symlink escape blocked]")
        if (isForbiddenExtension(relative)) return ExactFile(relative, error = "[SECURITY: binary or database file extension blocked]")
        val size = Files.size(realTarget)
        if (size > MAX_FILE_SIZE_BYTES) {
            return ExactFile(relative, error = "[file exceeds size limit of 1 MiB ($size bytes)]")
        }
        return try {
            ExactFile(relative, content = Files.readAllBytes(realTarget))
        } catch (error: Exception) {
            ExactFile(relative, error = "[read failed: ${error.javaClass.simpleName}]")
        }
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
        return RedactedFile(configSpec.path, render(configSpec.path, data))
    }

    fun readAndRedact(
        configSpec: DiagnosticConfiguration,
        rootDirectory: Path = dataFolder,
    ): Map<String, Any?> {
        val relPath = configSpec.path
        val result = linkedMapOf<String, Any?>()
        result["path"] = relPath

        val normalizedRoot = rootDirectory.toAbsolutePath().normalize()
        val targetPath = normalizedRoot.resolve(relPath).normalize()

        // ── Security Checks ──────────────────────────────────────────────────
        if (!targetPath.startsWith(normalizedRoot)) {
            result["error"] = "[SECURITY: path traversal blocked]"
            return result
        }
        if (Files.isSymbolicLink(targetPath)) {
            result["error"] = "[SECURITY: symlink escape blocked]"
            return result
        }
        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            result["error"] = "[file not found or not a regular file]"
            return result
        }
        val realRoot = try {
            normalizedRoot.toRealPath()
        } catch (_: Exception) {
            result["error"] = "[SECURITY: configuration root cannot be resolved]"
            return result
        }
        val realTarget = try {
            targetPath.toRealPath()
        } catch (_: Exception) {
            result["error"] = "[SECURITY: configuration path cannot be resolved]"
            return result
        }
        if (!realTarget.startsWith(realRoot)) {
            result["error"] = "[SECURITY: symlink escape blocked]"
            return result
        }
        if (isForbiddenExtension(relPath)) {
            result["error"] = "[SECURITY: binary or database file extension blocked]"
            return result
        }
        val size = Files.size(targetPath)
        if (size > MAX_FILE_SIZE_BYTES) {
            result["error"] = "[file exceeds size limit of 1 MiB ($size bytes)]"
            return result
        }

        // ── Parsing ──────────────────────────────────────────────────────────
        val rawMap = try {
            parseFile(targetPath.toFile(), relPath)
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

    private fun parseFile(file: File, relPath: String): Map<String, Any?> {
        val lower = relPath.lowercase()
        val text = file.readText(Charsets.UTF_8)

        return when {
            lower.endsWith(".yml") || lower.endsWith(".yaml") -> parseYaml(text)
            lower.endsWith(".json") -> parseJson(text)
            lower.endsWith(".properties") -> parseProperties(text)
            lower.endsWith(".toml") -> parseToml(text)
            lower.endsWith(".conf") -> parseConf(text)
            else -> mapOf("raw" to redactor.redact(text.take(4096)))
        }
    }

    private fun parseYaml(text: String): Map<String, Any?> {
        val options = LoaderOptions().apply {
            maxAliasesForCollections = 50
            isAllowDuplicateKeys = false
        }
        val yaml = Yaml(SafeConstructor(options))
        val loaded = yaml.load<Any>(text)
        return objectToMap(loaded)
    }

    private fun parseJson(text: String): Map<String, Any?> {
        val element = JsonParser.parseString(text)
        return objectToMap(Gson().fromJson(element, Any::class.java))
    }

    private fun parseProperties(text: String): Map<String, Any?> {
        val props = Properties()
        props.load(text.reader())
        val map = linkedMapOf<String, Any?>()
        for (name in props.stringPropertyNames()) {
            map[name] = props.getProperty(name)
        }
        return map
    }

    private fun parseToml(text: String): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        var currentSection = ""
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed.substring(1, trimmed.length - 1).trim()
                continue
            }
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val key = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                val fullKey = if (currentSection.isEmpty()) key else "$currentSection.$key"
                result[fullKey] = value
            }
        }
        return result
    }

    private fun parseConf(text: String): Map<String, Any?> {
        // HOCON / key-value parse fallback
        val result = linkedMapOf<String, Any?>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue
            val sepIdx = if (trimmed.indexOf('=') != -1) trimmed.indexOf('=') else trimmed.indexOf(':')
            if (sepIdx > 0) {
                val key = trimmed.substring(0, sepIdx).trim()
                val value = trimmed.substring(sepIdx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                result[key] = value
            }
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun objectToMap(obj: Any?): Map<String, Any?> {
        if (obj == null) return emptyMap()
        if (obj is Map<*, *>) {
            val res = linkedMapOf<String, Any?>()
            for ((k, v) in obj) {
                if (k != null) {
                    res[k.toString()] = transformValue(v)
                }
            }
            return res
        }
        return mapOf("value" to transformValue(obj))
    }

    private fun transformValue(v: Any?): Any? {
        if (v == null || v is Number || v is Boolean) return v
        if (v is Map<*, *>) return objectToMap(v)
        if (v is List<*>) return v.map { transformValue(it) }
        return v.toString()
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

    private fun isForbiddenExtension(path: String): Boolean {
        val lower = path.lowercase()
        return FORBIDDEN_EXTENSIONS.any { lower.endsWith(it) }
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

    private fun render(path: String, data: Map<String, Any?>): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".yml") || lower.endsWith(".yaml") -> Yaml().dump(data)
            lower.endsWith(".json") -> Gson().newBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(data) + "\n"
            lower.endsWith(".properties") -> data.entries.joinToString("\n", postfix = "\n") { (key, value) ->
                "$key=${scalar(value)}"
            }
            lower.endsWith(".toml") -> data.entries.joinToString("\n", postfix = "\n") { (key, value) ->
                "$key = ${tomlValue(value)}"
            }
            lower.endsWith(".conf") -> data.entries.joinToString("\n", postfix = "\n") { (key, value) ->
                "$key = ${tomlValue(value)}"
            }
            else -> data["raw"]?.toString().orEmpty()
        }
    }

    private fun scalar(value: Any?): String = value?.toString()
        ?.replace("\\", "\\\\")?.replace("\n", "\\n") ?: ""

    private fun tomlValue(value: Any?): String = when (value) {
        null -> "\"\""
        is Number, is Boolean -> value.toString()
        else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    }

    companion object {
        const val MAX_FILE_SIZE_BYTES = 1_048_576L // 1 MiB
        const val MAX_DEPTH = 10
        const val MAX_ENTRIES_PER_MAP = 200
        const val MAX_ENTRIES_PER_LIST = 100

        private val BUILTIN_SECRET_KEY = Pattern.compile(
            "(?i).*(?:password|passwd|pwd|secret|token|api[-_ ]?key|authorization|cookie|private[-_ ]?key|credential|webhook|mysql|auth|jdbc).*"
        )

        private val FORBIDDEN_EXTENSIONS = setOf(
            ".db", ".sqlite", ".sqlite3", ".db-shm", ".db-wal", ".bin", ".dat",
            ".class", ".jar", ".zip", ".tar", ".gz", ".png", ".jpg", ".jpeg", ".ico"
        )
    }
}
