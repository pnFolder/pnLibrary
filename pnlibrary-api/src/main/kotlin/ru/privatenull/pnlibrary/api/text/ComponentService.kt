package ru.privatenull.pnlibrary.api.text

import net.kyori.adventure.text.Component

enum class ComponentSerializerType {
    ADAPTIVE, MINI_MESSAGE, LEGACY_AMPERSAND, LEGACY_SECTION, ADVENTURE_JSON, PLAIN_TEXT
}

interface ComponentSerializer {
    val type: ComponentSerializerType
    fun deserialize(input: String): Component
    /** Deserializes every input independently and preserves list boundaries. */
    fun deserializeAll(inputs: Iterable<String>): List<Component> = inputs.map(::deserialize)
    /** Deserializes lines and joins them with real newline components, without a trailing newline. */
    fun deserializeLines(lines: Iterable<String>): Component = joinComponentLines(deserializeAll(lines))
    fun deserializeLines(vararg lines: String): Component = deserializeLines(lines.asList())
    fun serialize(component: Component): String
    fun serializeAll(components: Iterable<Component>): List<String> = components.map(::serialize)
}

enum class ComponentCacheScope { NONE, PLUGIN, GLOBAL }

data class ComponentCachePolicy @JvmOverloads constructor(
    val scope: ComponentCacheScope = ComponentCacheScope.PLUGIN,
    val maximumEntries: Int = 1_000,
    val expireAfterAccessMillis: Long = 30 * 60 * 1_000L,
) {
    init { require(maximumEntries > 0); require(expireAfterAccessMillis > 0) }
}

data class ComponentCacheStatistics(
    val hits: Long, val misses: Long, val evictions: Long, val size: Int,
) { val hitRate: Double get() = if (hits + misses == 0L) 0.0 else hits.toDouble() / (hits + misses) }

interface ComponentTemplate {
    fun value(name: String, value: Any?): ComponentTemplate
    fun player(playerId: java.util.UUID): ComponentTemplate
    fun render(): Component
}

interface ComponentService {
    var defaultSerializerType: ComponentSerializerType
    fun serializer(type: ComponentSerializerType): ComponentSerializer
    fun deserialize(input: String): Component
    fun deserialize(input: String, type: ComponentSerializerType): Component
    /** Returns one component per source string. */
    fun deserializeAll(inputs: Iterable<String>): List<Component>
    fun deserializeAll(inputs: Iterable<String>, type: ComponentSerializerType): List<Component>
    /** Returns one multiline component with `Component.newline()` between source strings. */
    fun deserializeLines(lines: Iterable<String>): Component
    fun deserializeLines(lines: Iterable<String>, type: ComponentSerializerType): Component
    fun deserializeLines(vararg lines: String): Component = deserializeLines(lines.asList())
    fun serialize(component: Component): String
    fun serializeAll(components: Iterable<Component>): List<String> = components.map(::serialize)
    fun template(input: String): ComponentTemplate
    fun configureCache(policy: ComponentCachePolicy)
    fun clearCache()
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
