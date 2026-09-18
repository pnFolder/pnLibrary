package ru.privatenull.pnlibrary.core.diagnostics

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.util.Properties

/**
 * Parses and renders configuration documents supported by diagnostic reports.
 *
 * The codec deliberately produces a neutral tree of maps, lists, scalar values,
 * and `null`. It performs no path validation or secret filtering; those concerns
 * belong to [SecureConfigurationFileReader] and [ConfigReader], respectively.
 * Unknown extensions are represented by a bounded `raw` value.
 */
internal class ConfigurationDocumentCodec(
    private val redactor: DiagnosticRedactor = DiagnosticRedactor(),
) {
    /** Parses [content] according to the extension of [path]. */
    fun parse(path: String, content: ByteArray): Map<String, Any?> {
        val text = content.toString(Charsets.UTF_8)
        val lowercasePath = path.lowercase()
        return when {
            lowercasePath.endsWith(".yml") || lowercasePath.endsWith(".yaml") -> parseYaml(text)
            lowercasePath.endsWith(".json") -> parseJson(text)
            lowercasePath.endsWith(".properties") -> parseProperties(text)
            lowercasePath.endsWith(".toml") -> parseToml(text)
            lowercasePath.endsWith(".conf") -> parseConf(text)
            else -> mapOf("raw" to redactor.redact(text.take(MAX_RAW_CHARACTERS)))
        }
    }

    /** Renders a neutral [data] tree using the extension of [path]. */
    fun render(path: String, data: Map<String, Any?>): String {
        val lowercasePath = path.lowercase()
        return when {
            lowercasePath.endsWith(".yml") || lowercasePath.endsWith(".yaml") -> Yaml().dump(data)
            lowercasePath.endsWith(".json") -> PRETTY_JSON.toJson(data) + "\n"
            lowercasePath.endsWith(".properties") -> renderAssignments(data, "=") { scalar(it) }
            lowercasePath.endsWith(".toml") || lowercasePath.endsWith(".conf") ->
                renderAssignments(data, " = ") { tomlValue(it) }
            else -> data["raw"]?.toString().orEmpty()
        }
    }

    private fun parseYaml(text: String): Map<String, Any?> {
        val options = LoaderOptions().apply {
            maxAliasesForCollections = MAX_YAML_ALIASES
            isAllowDuplicateKeys = false
        }
        return objectToMap(Yaml(SafeConstructor(options)).load<Any>(text))
    }

    private fun parseJson(text: String): Map<String, Any?> =
        objectToMap(GSON.fromJson(JsonParser.parseString(text), Any::class.java))

    private fun parseProperties(text: String): Map<String, Any?> {
        val properties = Properties().apply { load(text.reader()) }
        return properties.stringPropertyNames().associateWithTo(linkedMapOf()) {
            properties.getProperty(it)
        }
    }

    private fun parseToml(text: String): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        var section = ""
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                return@forEach
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.substring(1, trimmed.length - 1).trim()
                return@forEach
            }
            val separator = trimmed.indexOf('=')
            if (separator > 0) {
                val key = trimmed.substring(0, separator).trim()
                val qualifiedKey = if (section.isEmpty()) key else "$section.$key"
                result[qualifiedKey] = unquote(trimmed.substring(separator + 1))
            }
        }
        return result
    }

    private fun parseConf(text: String): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                return@forEach
            }
            val equals = trimmed.indexOf('=')
            val colon = trimmed.indexOf(':')
            val separator = when {
                equals > 0 -> equals
                colon > 0 -> colon
                else -> -1
            }
            if (separator > 0) {
                result[trimmed.substring(0, separator).trim()] = unquote(trimmed.substring(separator + 1))
            }
        }
        return result
    }

    private fun unquote(value: String): String =
        value.trim().removeSurrounding("\"").removeSurrounding("'")

    private fun objectToMap(value: Any?): Map<String, Any?> {
        if (value == null) return emptyMap()
        if (value !is Map<*, *>) return mapOf("value" to transform(value))
        return value.entries.mapNotNull { (key, child) ->
            key?.toString()?.let { it to transform(child) }
        }.toMap(linkedMapOf())
    }

    private fun transform(value: Any?): Any? = when (value) {
        null, is Number, is Boolean -> value
        is Map<*, *> -> objectToMap(value)
        is List<*> -> value.map(::transform)
        else -> value.toString()
    }

    private fun renderAssignments(
        data: Map<String, Any?>,
        separator: String,
        renderValue: (Any?) -> String,
    ): String = data.entries.joinToString("\n", postfix = "\n") { (key, value) ->
        "$key$separator${renderValue(value)}"
    }

    private fun scalar(value: Any?): String = value?.toString()
        ?.replace("\\", "\\\\")
        ?.replace("\n", "\\n")
        .orEmpty()

    private fun tomlValue(value: Any?): String = when (value) {
        null -> "\"\""
        is Number, is Boolean -> value.toString()
        else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    }

    private companion object {
        const val MAX_RAW_CHARACTERS = 4_096
        const val MAX_YAML_ALIASES = 50
        val GSON = Gson()
        val PRETTY_JSON = Gson().newBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    }
}
