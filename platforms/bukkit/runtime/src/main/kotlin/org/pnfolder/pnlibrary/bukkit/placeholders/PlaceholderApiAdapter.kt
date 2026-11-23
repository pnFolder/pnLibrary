package org.pnfolder.pnlibrary.bukkit.placeholders

import me.clip.placeholderapi.PlaceholderAPI
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PublishedAnswers
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** The server thread never waits: a late value is not answered at all. Other threads wait this long. */
internal const val MAIN_THREAD_WAIT_MILLIS = 0L
internal const val OTHER_THREAD_WAIT_MILLIS = 1000L

/**
 * Bridge to PlaceholderAPI. Each prefix the library publishes becomes one expansion: `%prefix_group_branch%` asks the
 * library for `group.branch`. Texts of the library may also contain `%tokens%` of other expansions.
 */
internal class PlaceholderApiAdapter(private val plugin: Plugin) : PlaceholderAdapter, AutoCloseable {
    private val expansions = ConcurrentHashMap<String, Expansion>()
    private val closed = AtomicBoolean(false)
    override val id = "placeholderapi"
    override val available: Boolean
        get() = !closed.get() && plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")

    override fun resolve(playerId: UUID?, expression: String): Any? {
        if (!available) return null
        val token = "%${expression.replace('.', '_')}%"
        return PlaceholderAPI.setPlaceholders(playerId?.let(plugin.server::getOfflinePlayer), token).takeUnless { it == token }
    }

    override fun render(playerId: UUID?, text: String): String {
        if (!available) return text
        return PlaceholderAPI.setPlaceholders(playerId?.let(plugin.server::getOfflinePlayer), text)
    }

    override fun publish(prefix: String, answers: PublishedAnswers): AutoCloseable {
        check(available) { "PlaceholderAPI is not installed" }
        require('_' !in prefix) { "PlaceholderAPI prefix '$prefix' must not contain '_'" }
        val expansion = Expansion(prefix, answers)
        check(expansions.putIfAbsent(prefix, expansion) == null) { "PlaceholderAPI prefix '$prefix' is already published" }
        check(expansion.register()) { "PlaceholderAPI did not accept the prefix '$prefix'" }
        return AutoCloseable {
            if (expansions.remove(prefix, expansion)) expansion.unregister()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        expansions.values.forEach(PlaceholderExpansion::unregister)
        expansions.clear()
    }

    private inner class Expansion(private val prefix: String, private val answers: PublishedAnswers) : PlaceholderExpansion() {
        override fun getIdentifier() = prefix
        override fun getAuthor() = this@PlaceholderApiAdapter.plugin.description.authors.joinToString(",").ifBlank { PnLibraryIdentity.NAME }
        override fun getVersion() = this@PlaceholderApiAdapter.plugin.description.version
        override fun persist() = true
        override fun onRequest(player: OfflinePlayer?, params: String): String? {
            val wait = if (this@PlaceholderApiAdapter.plugin.server.isPrimaryThread) MAIN_THREAD_WAIT_MILLIS else OTHER_THREAD_WAIT_MILLIS
            return answers.answer(player?.uniqueId, params, wait)
        }
    }
}
