package org.pnfolder.pnlibrary.bukkit.placeholders

import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRequest
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderResolver
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.UUID

/** Associates a published resolver with its portable owner. */
internal data class PublishedHandler(val owner: PluginId, val resolver: PlaceholderResolver<Any>)

/** Answers PlaceholderAPI's `onRequest` for one prefix from the names published under it. */
internal class PlaceholderApiRequests(private val onPrimaryThread: () -> Boolean) {
    val handlers = PublishedNames<PublishedHandler>()

    /** Finds the published name that fits [params] best and hands its parameters to the resolver; the server thread is told not to wait. */
    fun answer(playerId: UUID?, params: String): String? {
        val (handler, parameters) = handlers.find(params) ?: return null
        val wait = if (onPrimaryThread()) MAIN_THREAD_WAIT_MILLIS else OTHER_THREAD_WAIT_MILLIS
        val values = mapOf<String, Any?>(PlaceholderAdapter.WAIT_MILLIS_KEY to wait)
        return handler.resolver.resolve(PlaceholderRequest(handler.owner, handler.owner, playerId, parameters, values))?.toString()
    }
}
