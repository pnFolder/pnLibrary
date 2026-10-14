package ru.privatenull.pnlibrary.bukkit.placeholders

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.placeholders.ExternalPlaceholderRegistration
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAdapter
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAdapterCapabilities
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAdapterState
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderPublication
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderRequest
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderResolver
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Optional bridge that publishes pnLibrary placeholders to PlaceholderAPI. */
internal class PlaceholderApiAdapter(
    private val plugin: Plugin,
) : PlaceholderAdapter, AutoCloseable {
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

    /** Resolves an external token, returning null when the bridge or token is unavailable. */
    override fun resolve(playerId: UUID?, expression: String): Any? {
        if (state == PlaceholderAdapterState.UNAVAILABLE || state == PlaceholderAdapterState.CLOSED) return null
        val player = playerId?.let(plugin.server::getOfflinePlayer)
        val token = "%${expression.replace(':', '_').replace('.', '_')}%"
        val resolved = PlaceholderAPI.setPlaceholders(player, token)
        return resolved.takeUnless { it == token }
    }

    /** Publishes a resolver under a namespace and owns its external registration. */
    override fun publish(
        owner: PluginId,
        key: String,
        resolver: PlaceholderResolver<Any>,
        publication: PlaceholderPublication,
    ): ExternalPlaceholderRegistration {
        check(state != PlaceholderAdapterState.CLOSED) { "PlaceholderAPI adapter is closed" }
        check(plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) { "PlaceholderAPI is not installed" }
        val namespace = (publication.namespace ?: owner.value).lowercase(Locale.ROOT)
        val externalName = (publication.name ?: key.replace('.', '_')).lowercase(Locale.ROOT)
        val expansion = groups.computeIfAbsent(namespace) { Expansion(it) }
        if (!expansion.isRegistered) {
            check(expansion.register())
        }
        require(expansion.handlers.putIfAbsent(externalName, Handler(owner, resolver)) == null) {
            "Placeholder %${namespace}_${externalName}% is already published"
        }
        return object : ExternalPlaceholderRegistration {
            private val active = AtomicBoolean(true)
            override val state: PlaceholderAdapterState get() = if (active.get()) PlaceholderAdapterState.REGISTERED else PlaceholderAdapterState.CLOSED
            /** Unregisters all published expansions once. */
    override fun close() {
                if (!active.compareAndSet(true, false)) return
                expansion.handlers.remove(externalName)
                if (expansion.handlers.isEmpty()) {
                    expansion.unregister()
                    groups.remove(namespace, expansion)
                }
            }
        }
    }

    /** Unregisters all published expansions once. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        groups.values.forEach(PlaceholderExpansion::unregister)
        groups.clear()
    }

    /** Groups published resolvers under one PlaceholderAPI namespace. */
    private inner class Expansion(
        private val namespace: String,
    ) : PlaceholderExpansion() {
        val handlers = ConcurrentHashMap<String, Handler>()
        override fun getIdentifier() = namespace
        override fun getAuthor() = this@PlaceholderApiAdapter.plugin.description.authors.joinToString(",").ifBlank { "pnLibrary" }
        override fun getVersion() = this@PlaceholderApiAdapter.plugin.description.version
        override fun persist() = true
        /** Matches the longest published key and forwards its optional parameter to the resolver. */
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

    /** Associates a published resolver with its portable owner. */
    private data class Handler(
        val owner: PluginId,
        val resolver: PlaceholderResolver<Any>,
    )
}
