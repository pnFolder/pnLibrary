package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.PlaceholderFormatter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRequest
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Stores formatter definitions and applies formatter pipelines with owner-aware lookup. */
class PlaceholderFormatterRegistry {
    private val formatters = ConcurrentHashMap<String, FormatterDefinition<*>>()
    private val byAddress = ConcurrentHashMap<String, FormatterDefinition<*>>()
    private val systemOwner = PluginId.of(SYSTEM_NAMESPACE)

    /** Registers the formatters every plugin can use without an owner prefix. */
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
        register(systemOwner, "number", Any::class.java) { value, arguments, _ ->
            PlaceholderBuiltInFormatters.number(value, arguments)
        }
        register(systemOwner, "group", Any::class.java) { value, _, _ -> PlaceholderBuiltInFormatters.group(value) }
        register(systemOwner, "short", Any::class.java) { value, _, _ -> PlaceholderBuiltInFormatters.short(value) }
        register(systemOwner, "capitalize", Any::class.java) { value, _, _ -> PlaceholderBuiltInFormatters.capitalize(value) }
        register(systemOwner, "trim", Any::class.java) { value, _, _ -> value.toString().trim() }
        register(systemOwner, "round", Any::class.java) { value, arguments, _ -> PlaceholderBuiltInFormatters.round(value, arguments) }
        register(systemOwner, "percent", Any::class.java) { value, arguments, _ -> PlaceholderBuiltInFormatters.percent(value, arguments) }
        register(systemOwner, "money", Any::class.java) { value, arguments, _ -> PlaceholderBuiltInFormatters.money(value, arguments) }
        register(systemOwner, "pad", Any::class.java) { value, arguments, _ -> PlaceholderBuiltInFormatters.pad(value, arguments) }
        register(systemOwner, "title", Any::class.java) { value, _, _ -> PlaceholderBuiltInFormatters.title(value) }
        register(systemOwner, "date", Any::class.java) { value, arguments, _ -> PlaceholderBuiltInFormatters.date(value, arguments) }
    }

    /** Registers formatter [name] of [owner] for values of [type]; the name must be lower case letters, digits, `_` or `-`. */
    fun <T : Any> register(
        owner: PluginId,
        name: String,
        type: Class<T>,
        formatter: PlaceholderFormatter<T>,
    ) {
        require(name.matches(VALID_NAME)) { "Invalid formatter name: $name" }
        formatters[id(owner, name)] = FormatterDefinition(owner, type, formatter)
    }

    /** Makes a format reachable from any plugin as `plugin/module:name`. */
    fun <T : Any> registerAddress(
        owner: PluginId,
        address: String,
        type: Class<T>,
        formatter: PlaceholderFormatter<T>,
    ) {
        byAddress[address.lowercase(Locale.ROOT)] = FormatterDefinition(owner, type, formatter)
    }

    /** Removes every formatter and format address registered by [owner]. */
    fun removeOwnedBy(owner: PluginId) {
        formatters.entries.removeIf { (_, definition) -> definition.owner == owner }
        byAddress.entries.removeIf { (_, definition) -> definition.owner == owner }
    }

    /** Applies the formatters of [pipeline] to [value] in order; [own] are the formats of the placeholder's entry. */
    fun format(
        consumer: PluginId,
        value: Any?,
        pipeline: List<String>,
        request: PlaceholderRequest,
        own: Map<String, FormatterDefinition<*>> = emptyMap(),
    ): Any? {
        var current = value
        pipeline.forEach { expression ->
            val (name, rest) = splitName(expression)
            val arguments = rest.split(',').filter(String::isNotBlank)
            if (name == "default" && (current == null || current.toString().isBlank())) {
                current = arguments.joinToString(",")
            } else {
                current = applyFormatter(request.owner, consumer, name, current, arguments, request, own)
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
        own: Map<String, FormatterDefinition<*>>,
    ): Any? {
        if (value == null) return null
        val definition = own[name.lowercase(Locale.ROOT)]
            ?: byAddress[name.lowercase(Locale.ROOT)].takeIf { '/' in name }
            ?: formatters[id(owner, name)]
            ?: formatters[id(consumer, name)]
            ?: formatters[id(systemOwner, name)]
            ?: return value
        if (!definition.type.isInstance(value)) return value

        @Suppress("UNCHECKED_CAST")
        return (definition as FormatterDefinition<Any>).formatter.format(value, arguments, request)
    }

    /** The name and the arguments of `pad:5,0`; a name with a slash is an address and keeps its colon: `plugin/module:rank:5`. */
    private fun splitName(expression: String): Pair<String, String> {
        val head = expression.substringBefore(':')
        if ('/' !in head) return head.trim() to expression.substringAfter(':', "")
        val afterAddress = expression.substringAfter(':', "")
        return (head + ":" + afterAddress.substringBefore(':')).trim() to afterAddress.substringAfter(':', "")
    }

    private fun id(owner: PluginId, name: String): String =
        "${owner.value}:${name.lowercase(Locale.ROOT)}"

    /** A format with the type of value it accepts; [owner] is the plugin that registered it. */
    data class FormatterDefinition<T : Any>(
        val owner: PluginId,
        val type: Class<T>,
        val formatter: PlaceholderFormatter<T>,
    )

    internal companion object {
        const val SYSTEM_NAMESPACE = PnLibraryIdentity.PRODUCT_ID
        val VALID_NAME = Regex("[a-z0-9_-]+")
        /** The formats to run: the text's own, else the placeholder's default; `raw` switches the default off. */
        fun pipelineOf(default: String?, fromText: List<String>): List<String> {
            val explicit = fromText.map(String::trim).filterNot { it == RAW }
            if (explicit.isNotEmpty() || fromText.any { it.trim() == RAW }) return explicit
            return listOfNotNull(default)
        }

        private const val RAW = "raw"
    }
}
