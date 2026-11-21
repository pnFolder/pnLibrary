package org.pnfolder.pnlibrary.api.text.serializer

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
    fun deserializeLines(lines: Iterable<String>): Component = deserializeAll(lines).joinLines()

    /** Vararg convenience overload for [deserializeLines]. */
    fun deserializeLines(vararg lines: String): Component = deserializeLines(lines.asList())

    /** Serializes [component] using this serializer's syntax. */
    fun serialize(component: Component): String

    /** Serializes every component independently and preserves list boundaries. */
    fun serializeAll(components: Iterable<Component>): List<String> = components.map(::serialize)
}

/** Joins components with `Component.newline()` between them, or returns an empty component for none. */
internal fun Iterable<Component>.joinLines(): Component {
    val iterator = iterator()
    if (!iterator.hasNext()) return Component.empty()
    var result = iterator.next()
    while (iterator.hasNext()) result = result.append(Component.newline()).append(iterator.next())
    return result
}
