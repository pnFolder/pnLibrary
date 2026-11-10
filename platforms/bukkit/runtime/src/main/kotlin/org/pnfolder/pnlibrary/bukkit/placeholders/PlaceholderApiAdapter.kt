package org.pnfolder.pnlibrary.bukkit.placeholders

import me.clip.placeholderapi.PlaceholderAPI
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.placeholders.ExternalPlaceholderRegistration
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterCapabilities
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterState
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderPublication
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRequest
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderResolver
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
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

    /** Lets PlaceholderAPI replace every `%...%` it knows in [text]; the rest stays. */
    override fun render(playerId: UUID?, text: String): String {
        if (state == PlaceholderAdapterState.UNAVAILABLE || state == PlaceholderAdapterState.CLOSED) return text
        return PlaceholderAPI.setPlaceholders(playerId?.let(plugin.server::getOfflinePlayer), text)
    }

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
        require('_' !in namespace) { "PlaceholderAPI prefix '$namespace' must not contain '_'" }
        val expansion = groups.computeIfAbsent(namespace) { Expansion(it) }
        if (!expansion.isRegistered) {
            check(expansion.register())
        }
        require(expansion.handlers.add(externalName, Handler(owner, resolver))) {
            "Placeholder %${namespace}_${externalName}% is already published"
        }
        return object : ExternalPlaceholderRegistration {
            private val active = AtomicBoolean(true)
            override val state: PlaceholderAdapterState get() = if (active.get()) PlaceholderAdapterState.REGISTERED else PlaceholderAdapterState.CLOSED
    override fun close() {
                if (!active.compareAndSet(true, false)) return
                expansion.handlers.remove(externalName)
                if (expansion.handlers.isEmpty) {
                    expansion.unregister()
                    groups.remove(namespace, expansion)
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        groups.values.forEach(PlaceholderExpansion::unregister)
        groups.clear()
    }

    /** Groups published resolvers under one PlaceholderAPI namespace. */
    private inner class Expansion(
        private val namespace: String,
    ) : PlaceholderExpansion() {
        val handlers = PublishedNames<Handler>()
        override fun getIdentifier() = namespace
        override fun getAuthor() = this@PlaceholderApiAdapter.plugin.description.authors.joinToString(",").ifBlank { PnLibraryIdentity.NAME }
        override fun getVersion() = this@PlaceholderApiAdapter.plugin.description.version
        override fun persist() = true
        /** Finds the published name that fits [params] best and hands its parameters to the resolver. */
        override fun onRequest(player: OfflinePlayer?, params: String): String? {
            val wait = if (this@PlaceholderApiAdapter.plugin.server.isPrimaryThread) MAIN_THREAD_WAIT_MILLIS else OTHER_THREAD_WAIT_MILLIS
            val values = mapOf<String, Any?>(PlaceholderAdapter.WAIT_MILLIS_KEY to wait)
            val (handler, parameters) = handlers.find(params) ?: return null
            return answer(handler, PlaceholderRequest(handler.owner, handler.owner, player?.uniqueId, parameters, values))
        }

        private fun answer(handler: Handler, request: PlaceholderRequest): String? =
            handler.resolver.resolve(request)?.toString()
    }

    /** Associates a published resolver with its portable owner. */
    private data class Handler(
        val owner: PluginId,
        val resolver: PlaceholderResolver<Any>,
    )
}

/** The server thread never waits: a late value is answered from the last known one and warmed up for the next ask. */
internal const val MAIN_THREAD_WAIT_MILLIS = 0L
internal const val OTHER_THREAD_WAIT_MILLIS = 1000L
