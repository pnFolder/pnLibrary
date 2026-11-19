package org.pnfolder.pnlibrary.api.text

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.text.cache.ComponentCachePolicy
import org.pnfolder.pnlibrary.api.text.cache.ComponentCacheStatistics
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializer
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType

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

    /** Parses [input] into a reusable template using the default serializer. */
    fun template(input: String): ComponentTemplate

    /** Parses [input] into a reusable template; the serializer type is ignored. */
    fun template(input: String, type: ComponentSerializerType): ComponentTemplate = template(input)

    /** Replaces the active parsed-component cache policy. */
    fun configureCache(policy: ComponentCachePolicy)

    /** Removes all parsed components from the active cache. */
    fun clearCache()

    /** Returns current cache counters and retained size. */
    fun cacheStatistics(): ComponentCacheStatistics
}
