package ru.privatenull.pnlibrary.api.text

import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderRequest

enum class ComponentSerializerType {
    ADAPTIVE, MINI_MESSAGE, LEGACY_AMPERSAND, LEGACY_SECTION, ADVENTURE_JSON, PLAIN_TEXT
}

interface ComponentSerializer {
    val type: ComponentSerializerType
    fun deserialize(input: String): Component
    fun serialize(component: Component): String
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
    fun serialize(component: Component): String
    fun template(input: String): ComponentTemplate
    fun configureCache(policy: ComponentCachePolicy)
    fun clearCache()
    fun cacheStatistics(): ComponentCacheStatistics
}
