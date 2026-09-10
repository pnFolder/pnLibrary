package ru.privatenull.pnlibrary.api.placeholders

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletionStage

data class PlaceholderKey<T : Any>(val value: String, val type: Class<T>) {
    init { require(value.matches(Regex("[a-z0-9_.{}-]+"))) { "Invalid placeholder key: $value" } }
    companion object { @JvmStatic fun <T : Any> of(value: String, type: Class<T>) = PlaceholderKey(value, type) }
}

data class PlaceholderRequest(
    val owner: PluginId,
    val consumer: PluginId,
    val playerId: UUID?,
    val parameters: Map<String, String>,
    val values: Map<String, Any?>,
) {
    fun requirePlayerId(): UUID = playerId ?: error("This placeholder requires a player")
    fun parameter(name: String): String = parameters[name] ?: error("Missing placeholder parameter: $name")
}

fun interface PlaceholderResolver<T : Any> { fun resolve(request: PlaceholderRequest): T? }
fun interface AsyncPlaceholderResolver<T : Any> { fun resolve(request: PlaceholderRequest): CompletionStage<T?> }

class PlaceholderAccess private constructor(
    val ownerAllowed: Boolean,
    val allLibraryPlugins: Boolean,
    val allowedPlugins: Set<PluginId>,
    val allowedPatterns: Set<String>,
    val deniedPlugins: Set<PluginId>,
) {
    fun allows(owner: PluginId, consumer: PluginId): Boolean {
        if (consumer in deniedPlugins) return false
        if (ownerAllowed && owner == consumer) return true
        if (allLibraryPlugins || consumer in allowedPlugins) return true
        return allowedPatterns.any { wildcard(it, consumer.value) }
    }
    companion object {
        @JvmStatic fun ownerOnly() = Builder().owner().build()
        @JvmStatic fun shared() = Builder().owner().allowAllLibraryPlugins().build()
        @JvmStatic fun builder() = Builder()
        private fun wildcard(pattern: String, value: String) = Regex(
            "^" + pattern.split('*').joinToString(".*", transform = Regex::escape) + "$", RegexOption.IGNORE_CASE
        ).matches(value)
    }
    class Builder {
        private var owner = false; private var all = false
        private val allow = linkedSetOf<PluginId>(); private val patterns = linkedSetOf<String>(); private val deny = linkedSetOf<PluginId>()
        fun owner() = apply { owner = true }
        fun allowAllLibraryPlugins() = apply { all = true }
        fun allow(vararg ids: String) = apply { ids.map(PluginId::of).forEach(allow::add) }
        fun allowMatching(vararg values: String) = apply { patterns += values }
        fun deny(vararg ids: String) = apply { ids.map(PluginId::of).forEach(deny::add) }
        fun build() = PlaceholderAccess(owner, all, allow.toSet(), patterns.toSet(), deny.toSet())
    }
}

enum class PlaceholderCacheScope { NONE, GLOBAL, PLUGIN, PLAYER, ARGUMENTS }
data class PlaceholderCachePolicy @JvmOverloads constructor(
    val scope: PlaceholderCacheScope = PlaceholderCacheScope.NONE,
    val maximumEntries: Int = 1_000,
    val expireAfterWriteMillis: Long = 30_000,
    val cacheNullValues: Boolean = false,
) {
    init { require(maximumEntries > 0); require(expireAfterWriteMillis > 0) }
    companion object {
        @JvmStatic fun none() = PlaceholderCachePolicy()
        @JvmStatic fun player(duration: Duration) = PlaceholderCachePolicy(PlaceholderCacheScope.PLAYER, 10_000, duration.toMillis())
    }
}

enum class PlaceholderAdapterState { AVAILABLE, UNAVAILABLE, REGISTERED, FAILED, CLOSED }
data class PlaceholderAdapterCapabilities(
    val players: Boolean = true, val offlinePlayers: Boolean = false,
    val parameters: Boolean = true, val components: Boolean = false, val asynchronous: Boolean = false,
)

interface ExternalPlaceholderRegistration : AutoCloseable { val state: PlaceholderAdapterState }
data class PlaceholderPublication(val adapterId: String, val namespace: String? = null, val name: String? = null)

interface PlaceholderAdapter {
    val id: String
    val state: PlaceholderAdapterState
    val capabilities: PlaceholderAdapterCapabilities
    /** Resolves a value owned by the external platform, or returns `null` when unknown. */
    fun resolve(playerId: UUID?, expression: String): Any? = null
    fun publish(owner: PluginId, key: String, resolver: PlaceholderResolver<Any>, publication: PlaceholderPublication): ExternalPlaceholderRegistration
}

interface PlaceholderAdapterRegistry {
    fun register(adapter: PlaceholderAdapter): AutoCloseable
    fun get(id: String): PlaceholderAdapter?
    fun require(id: String): PlaceholderAdapter = get(id) ?: error("Placeholder adapter $id is unavailable")
    fun all(): List<PlaceholderAdapter>
}
