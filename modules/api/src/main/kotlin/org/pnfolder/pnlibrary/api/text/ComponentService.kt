package org.pnfolder.pnlibrary.api.text

import net.kyori.adventure.text.Component

/**
 * Bidirectional converter for one [ComponentSerializerType].
 *
 * Implementations must be safe for repeated use. Unless stated by an implementation,
 * malformed input may throw a format-specific runtime exception.
 */
interface ComponentSerializer {

    /** Syntax handled by this serializer. */
    val type: ComponentSerializerType

    /** Parses one [input] string into an Adventure component. */
    fun deserialize(input: String): Component

    /** Deserializes every input independently and preserves list boundaries. */
    fun deserializeAll(inputs: Iterable<String>): List<Component> = inputs.map(::deserialize)

    /** Deserializes lines and joins them with real newline components, without a trailing newline. */
    fun deserializeLines(lines: Iterable<String>): Component = joinComponentLines(deserializeAll(lines))

    /** Vararg convenience overload for [deserializeLines]. */
    fun deserializeLines(vararg lines: String): Component = deserializeLines(lines.asList())

    /** Serializes [component] using this serializer's syntax. */
    fun serialize(component: Component): String

    /** Serializes every component independently and preserves list boundaries. */
    fun serializeAll(components: Iterable<Component>): List<String> = components.map(::serialize)
}

/** Ownership boundary used for parsed-component cache entries. */
enum class ComponentCacheScope {
    /** Bypasses the cache and parses every request. */
    NONE,
    /** Isolates entries by the plugin that owns the component service. */
    PLUGIN,
    /** Allows compatible service instances to share cached entries. */
    GLOBAL,
}

/**
 * Central text conversion, templating, and cache service.
 *
 * Overloads without an explicit serializer use [defaultSerializerType]. Returned
 * components are Adventure values and can be safely further composed by callers.
 */
interface ComponentService {

    /** Serializer selected by overloads that do not accept an explicit type. */
    var defaultSerializerType: ComponentSerializerType

    /** Returns the registered serializer for [type]. */
    fun serializer(type: ComponentSerializerType): ComponentSerializer

    /** Parses [input] with [defaultSerializerType]. */
    fun deserialize(input: String): Component

    /** Parses [input] with the selected serializer [type]. */
    fun deserialize(input: String, type: ComponentSerializerType): Component

    /** Returns one component per source string. */
    fun deserializeAll(inputs: Iterable<String>): List<Component>

    /** Parses each input independently with [type]. */
    fun deserializeAll(inputs: Iterable<String>, type: ComponentSerializerType): List<Component>

    /** Returns one multiline component with `Component.newline()` between source strings. */
    fun deserializeLines(lines: Iterable<String>): Component

    /** Parses and joins [lines] with explicit serializer [type]. */
    fun deserializeLines(lines: Iterable<String>, type: ComponentSerializerType): Component

    /** Vararg convenience overload using [defaultSerializerType]. */
    fun deserializeLines(vararg lines: String): Component = deserializeLines(lines.asList())

    /** Serializes [component] with [defaultSerializerType]. */
    fun serialize(component: Component): String

    /** Serializes each component independently with [defaultSerializerType]. */
    fun serializeAll(components: Iterable<Component>): List<String> = components.map(::serialize)

    /** Creates an isolated rendering invocation for [input]. */
    fun template(input: String): ComponentTemplate

    /** Creates a rendering invocation using the explicit serializer [type]. */
    fun template(input: String, type: ComponentSerializerType): ComponentTemplate = template(input)

    /** Replaces the active parsed-component cache policy. */
    fun configureCache(policy: ComponentCachePolicy)

    /** Removes all parsed components from the active cache. */
    fun clearCache()

    /** Returns current cache counters and retained size. */
    fun cacheStatistics(): ComponentCacheStatistics
}

private fun joinComponentLines(lines: Iterable<Component>): Component {
    val iterator = lines.iterator()
    if (!iterator.hasNext()) return Component.empty()
    var result = iterator.next()
    while (iterator.hasNext()) {
        result = result.append(Component.newline()).append(iterator.next())
    }
    return result
}
