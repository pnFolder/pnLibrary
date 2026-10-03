package ru.privatenull.pnlibrary.core.placeholders

import ru.privatenull.pnlibrary.api.placeholders.PlaceholderFormatter
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderRequest
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Stores formatter definitions and applies formatter pipelines with owner-aware lookup. */
internal class PlaceholderFormatterRegistry {
    private val formatters = ConcurrentHashMap<String, FormatterDefinition<*>>()
    private val systemOwner = PluginId.of(SYSTEM_NAMESPACE)

    fun registerBuiltIns() {
        register(systemOwner, "upper", Any::class.java) { value, _, _ -> value.toString().uppercase(Locale.ROOT) }
        register(systemOwner, "lower", Any::class.java) { value, _, _ -> value.toString().lowercase(Locale.ROOT) }
        register(systemOwner, "default", Any::class.java) { value, _, _ -> value.toString() }
        register(systemOwner, "boolean", Any::class.java) { value, arguments, _ ->
            if (value == true) arguments.getOrElse(0) { "true" } else arguments.getOrElse(1) { "false" }
        }
        register(systemOwner, "plural", Any::class.java) { value, arguments, _ ->
            PlaceholderBuiltInFormatters.plural(value, arguments)
        }
        register(systemOwner, "duration", Any::class.java) { value, _, _ ->
            PlaceholderBuiltInFormatters.duration(value)
        }
    }

    fun <T : Any> register(
        owner: PluginId,
        name: String,
        type: Class<T>,
        formatter: PlaceholderFormatter<T>,
    ) {
        require(name.matches(VALID_NAME)) { "Invalid formatter name: $name" }
        formatters[id(owner, name)] = FormatterDefinition(owner, type, formatter)
    }

    fun removeOwnedBy(owner: PluginId) {
        formatters.entries.removeIf { (_, definition) -> definition.owner == owner }
    }

    fun format(
        consumer: PluginId,
        value: Any?,
        pipeline: List<String>,
        request: PlaceholderRequest,
    ): Any? {
        var current = value
        pipeline.forEach { expression ->
            val name = expression.substringBefore(':').trim()
            val arguments = expression.substringAfter(':', "").split(',').filter(String::isNotBlank)
            if (name == "default" && (current == null || current.toString().isBlank())) {
                current = arguments.joinToString(",")
            } else {
                current = applyFormatter(request.owner, consumer, name, current, arguments, request)
            }
        }
        return current
    }

    private fun applyFormatter(
        owner: PluginId,
        consumer: PluginId,
        name: String,
        value: Any?,
        arguments: List<String>,
        request: PlaceholderRequest,
    ): Any? {
        if (value == null) return null
        val definition = formatters[id(owner, name)]
            ?: formatters[id(consumer, name)]
            ?: formatters[id(systemOwner, name)]
            ?: return value
        if (!definition.type.isInstance(value)) return value

        @Suppress("UNCHECKED_CAST")
        return (definition as FormatterDefinition<Any>).formatter.format(value, arguments, request)
    }

    private fun id(owner: PluginId, name: String): String =
        "${owner.value}:${name.lowercase(Locale.ROOT)}"

    private data class FormatterDefinition<T : Any>(
        val owner: PluginId,
        val type: Class<T>,
        val formatter: PlaceholderFormatter<T>,
    )

    private companion object {
        const val SYSTEM_NAMESPACE = "pnlibrary"
        val VALID_NAME = Regex("[a-z0-9_-]+")
    }
}
