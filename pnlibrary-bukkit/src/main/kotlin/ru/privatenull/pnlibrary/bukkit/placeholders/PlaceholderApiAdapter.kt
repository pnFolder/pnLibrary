package ru.privatenull.pnlibrary.bukkit.placeholders

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.placeholders.*
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Optional bridge that publishes pnLibrary placeholders to PlaceholderAPI. */
class PlaceholderApiAdapter(private val plugin: Plugin) : PlaceholderAdapter, AutoCloseable {
    private val groups = ConcurrentHashMap<String, Expansion>()
    private val closed = AtomicBoolean(false)
    override val id = "placeholderapi"
    override val state: PlaceholderAdapterState get() = when {
        closed.get() -> PlaceholderAdapterState.CLOSED
        !plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI") -> PlaceholderAdapterState.UNAVAILABLE
        groups.isEmpty() -> PlaceholderAdapterState.AVAILABLE
        else -> PlaceholderAdapterState.REGISTERED
    }
    override val capabilities = PlaceholderAdapterCapabilities(
        players = true, offlinePlayers = true, parameters = true, components = false, asynchronous = false,
    )

    override fun resolve(playerId: java.util.UUID?, expression: String): Any? {
        if (state == PlaceholderAdapterState.UNAVAILABLE || state == PlaceholderAdapterState.CLOSED) return null
        val player = playerId?.let(plugin.server::getOfflinePlayer)
        val token = "%${expression.replace(':', '_').replace('.', '_')}%"
        val resolved = PlaceholderAPI.setPlaceholders(player, token)
        return resolved.takeUnless { it == token }
    }

    override fun publish(
        owner: PluginId, key: String, resolver: PlaceholderResolver<Any>, publication: PlaceholderPublication,
    ): ExternalPlaceholderRegistration {
        check(state != PlaceholderAdapterState.CLOSED) { "PlaceholderAPI adapter is closed" }
        check(plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) { "PlaceholderAPI is not installed" }
        val namespace = (publication.namespace ?: owner.value).lowercase()
        val externalName = (publication.name ?: key.replace('.', '_')).lowercase()
        val expansion = groups.computeIfAbsent(namespace) { Expansion(it) }.also { if (!it.isRegistered) check(it.register()) }
        require(expansion.handlers.putIfAbsent(externalName, Handler(owner, resolver)) == null) {
            "Placeholder %${namespace}_${externalName}% is already published"
        }
        return object : ExternalPlaceholderRegistration {
            private val active = AtomicBoolean(true)
            override val state: PlaceholderAdapterState get() = if (active.get()) PlaceholderAdapterState.REGISTERED else PlaceholderAdapterState.CLOSED
            override fun close() {
                if (!active.compareAndSet(true, false)) return
                expansion.handlers.remove(externalName)
                if (expansion.handlers.isEmpty()) { expansion.unregister(); groups.remove(namespace, expansion) }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        groups.values.forEach(PlaceholderExpansion::unregister)
        groups.clear()
    }

    private inner class Expansion(private val namespace: String) : PlaceholderExpansion() {
        val handlers = ConcurrentHashMap<String, Handler>()
        override fun getIdentifier() = namespace
        override fun getAuthor() = this@PlaceholderApiAdapter.plugin.description.authors.joinToString(",").ifBlank { "pnLibrary" }
        override fun getVersion() = this@PlaceholderApiAdapter.plugin.description.version
        override fun persist() = true
        override fun onRequest(player: OfflinePlayer?, params: String): String? {
            val match = handlers.entries.sortedByDescending { it.key.length }.firstOrNull {
                params.equals(it.key, true) || params.startsWith(it.key + "_", true)
            } ?: return null
            val suffix = params.removePrefix(match.key).removePrefix("_")
            val request = PlaceholderRequest(
                match.value.owner, match.value.owner, player?.uniqueId,
                if (suffix.isEmpty()) emptyMap() else mapOf("value" to suffix), emptyMap(),
            )
            return match.value.resolver.resolve(request)?.toString()
        }
    }

    private data class Handler(val owner: PluginId, val resolver: PlaceholderResolver<Any>)
}
