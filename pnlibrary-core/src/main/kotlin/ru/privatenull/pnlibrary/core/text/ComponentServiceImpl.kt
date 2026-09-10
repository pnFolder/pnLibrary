package ru.privatenull.pnlibrary.core.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderService
import ru.privatenull.pnlibrary.api.text.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal class ComponentServiceImpl(
    private val placeholders: PlaceholderService,
    private val sharedCache: ComponentCache,
) : ComponentService {
    private val serializers = ComponentSerializerType.values().associateWith(::serializerFor)
    private var cachePolicy = ComponentCachePolicy()
    private val pluginCache = ComponentCache()
    override var defaultSerializerType = ComponentSerializerType.ADAPTIVE

    override fun serializer(type: ComponentSerializerType): ComponentSerializer = serializers.getValue(type)
    override fun deserialize(input: String): Component = deserialize(input, defaultSerializerType)
    override fun deserialize(input: String, type: ComponentSerializerType): Component {
        val cache = when (cachePolicy.scope) {
            ComponentCacheScope.NONE -> null
            ComponentCacheScope.PLUGIN -> pluginCache
            ComponentCacheScope.GLOBAL -> sharedCache
        }
        val key = "${type.name}\u0000$input"
        return cache?.get(key, cachePolicy) ?: serializer(type).deserialize(input).also {
            cache?.put(key, it, cachePolicy)
        }
    }
    override fun serialize(component: Component): String = serializer(defaultSerializerType).serialize(component)
    override fun template(input: String): ComponentTemplate = Template(input)
    override fun configureCache(policy: ComponentCachePolicy) {
        require(policy.maximumEntries > 0) { "maximumEntries must be positive" }
        require(policy.expireAfterAccessMillis > 0) { "expireAfterAccessMillis must be positive" }
        cachePolicy = policy
        pluginCache.trim(policy.maximumEntries)
    }
    override fun clearCache() { pluginCache.clear(); if (cachePolicy.scope == ComponentCacheScope.GLOBAL) sharedCache.clear() }
    override fun cacheStatistics(): ComponentCacheStatistics =
        (if (cachePolicy.scope == ComponentCacheScope.GLOBAL) sharedCache else pluginCache).statistics()

    private inner class Template(private val source: String) : ComponentTemplate {
        private val values = linkedMapOf<String, Any?>()
        private var playerId: UUID? = null
        override fun value(name: String, value: Any?) = apply { values[name] = value }
        override fun player(playerId: UUID) = apply { this.playerId = playerId }
        override fun render(): Component = serializer(defaultSerializerType)
            .deserialize(placeholders.render(source, playerId, values).toCompletableFuture().join())
    }

    private fun serializerFor(type: ComponentSerializerType): ComponentSerializer = object : ComponentSerializer {
        override val type = type
        override fun deserialize(input: String): Component = when (type) {
            ComponentSerializerType.ADAPTIVE -> adaptive(input)
            ComponentSerializerType.MINI_MESSAGE -> MINI.deserialize(input)
            ComponentSerializerType.LEGACY_AMPERSAND -> AMP.deserialize(input)
            ComponentSerializerType.LEGACY_SECTION -> SECTION.deserialize(input)
            ComponentSerializerType.ADVENTURE_JSON -> GSON.deserialize(input)
            ComponentSerializerType.PLAIN_TEXT -> Component.text(input)
        }
        override fun serialize(component: Component): String = when (type) {
            ComponentSerializerType.ADAPTIVE, ComponentSerializerType.MINI_MESSAGE -> MINI.serialize(component)
            ComponentSerializerType.LEGACY_AMPERSAND -> AMP.serialize(component)
            ComponentSerializerType.LEGACY_SECTION -> SECTION.serialize(component)
            ComponentSerializerType.ADVENTURE_JSON -> GSON.serialize(component)
            ComponentSerializerType.PLAIN_TEXT -> PLAIN.serialize(component)
        }
    }

    private fun adaptive(input: String): Component {
        if (input.isBlank()) return Component.empty()
        val trimmed = input.trimStart()
        if ((trimmed.startsWith('{') || trimmed.startsWith('[')) && runCatching { GSON.deserialize(input) }.isSuccess)
            return GSON.deserialize(input)
        return MINI.deserialize(toMiniMessage(input))
    }

    private fun toMiniMessage(source: String): String {
        var value = source.replace('§', '&')
        value = OLD_GRADIENT.replace(value) { "<gradient:#${it.groupValues[1]}:#${it.groupValues[3]}>${it.groupValues[2]}</gradient>" }
        value = HEX.replace(value) { "<#${it.groupValues[1]}>" }
        return LEGACY.replace(value) { LEGACY_TAGS[it.groupValues[1].lowercase()] ?: it.value }
    }

    companion object {
        private val MINI = MiniMessage.miniMessage()
        private val AMP = LegacyComponentSerializer.legacyAmpersand()
        private val SECTION = LegacyComponentSerializer.legacySection()
        private val GSON = GsonComponentSerializer.gson()
        private val PLAIN = PlainTextComponentSerializer.plainText()
        private val OLD_GRADIENT = Regex("<&?#([0-9a-fA-F]{6})>([^<]*)</&?#([0-9a-fA-F]{6})>")
        private val HEX = Regex("(?i)(?<!<)&?#([0-9a-f]{6})(?![^<]*>)")
        private val LEGACY = Regex("&([0-9a-fk-or])", RegexOption.IGNORE_CASE)
        private val LEGACY_TAGS = mapOf(
            "0" to "<black>", "1" to "<dark_blue>", "2" to "<dark_green>", "3" to "<dark_aqua>",
            "4" to "<dark_red>", "5" to "<dark_purple>", "6" to "<gold>", "7" to "<gray>",
            "8" to "<dark_gray>", "9" to "<blue>", "a" to "<green>", "b" to "<aqua>",
            "c" to "<red>", "d" to "<light_purple>", "e" to "<yellow>", "f" to "<white>",
            "k" to "<obfuscated>", "l" to "<bold>", "m" to "<strikethrough>", "n" to "<underlined>",
            "o" to "<italic>", "r" to "<reset>",
        )
    }
}

internal class ComponentCache {
    private data class Value(val component: Component, var accessed: Long)
    private val values = java.util.Collections.synchronizedMap(LinkedHashMap<String, Value>(16, .75f, true))
    private val hits = AtomicLong(); private val misses = AtomicLong(); private val evictions = AtomicLong()
    fun get(key: String, policy: ComponentCachePolicy): Component? = synchronized(values) {
        val value = values[key]
        if (value == null || System.currentTimeMillis() - value.accessed >= policy.expireAfterAccessMillis) {
            if (value != null) values.remove(key)
            misses.incrementAndGet(); null
        } else { value.accessed = System.currentTimeMillis(); hits.incrementAndGet(); value.component }
    }
    fun put(key: String, component: Component, policy: ComponentCachePolicy) = synchronized(values) {
        values[key] = Value(component, System.currentTimeMillis()); trim(policy.maximumEntries)
    }
    fun trim(maximum: Int) = synchronized(values) { while (values.size > maximum) { values.remove(values.keys.first()); evictions.incrementAndGet() } }
    fun clear() = values.clear()
    fun statistics() = ComponentCacheStatistics(hits.get(), misses.get(), evictions.get(), values.size)
}
