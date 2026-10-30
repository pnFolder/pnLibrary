package org.pnfolder.pnlibrary.core.link

import java.time.Instant

/**
 * The proxy end of the link: takes a plugin message that arrived from a game server.
 *
 * The platform listener tells it whether the sender was a server connection or a player; a message from a player
 * is dropped before its signature is even looked at, and a message without the right signature is dropped too.
 * A valid message is a report (remembered), a request for another server (passed on, or carried out here when the
 * proxy is the target) or the answer to a request (passed on to whoever asked).
 *
 * @param key the shared key, or null when the proxy has none, which turns the link off
 * @param executor carries out requests addressed to the proxy
 * @param transport sends to game servers by name
 */
class LinkReceiver(
    private val key: ByteArray?,
    private val registry: LinkRegistry = LinkHub.registry,
    private val executor: RemoteExecutor? = null,
    private val transport: LinkTransport? = null,
    private val replies: RemoteReplies = LinkHub.replies,
) {
    /** Whether the link can accept anything at all. */
    val enabled: Boolean get() = key != null

    /** Handles [message] from [server]; returns true when it was a valid message. */
    fun receive(server: String, fromServer: Boolean, message: ByteArray): Boolean {
        if (!fromServer || key == null) return false
        LinkCodec.decode(message, key)?.let {
            registry.accept(server, it)
            return true
        }
        LinkCodec.decodeRequest(message, key)?.let {
            route(server, it.copy(origin = server), key)
            return true
        }
        LinkCodec.decodeResult(message, key)?.let {
            deliver(it, key)
            return true
        }
        return false
    }

    private fun route(server: String, request: RemoteRequest, key: ByteArray) {
        val toOrigin = { result: RemoteResult -> transport?.send(server, LinkCodec.encodeResult(result, key)); Unit }
        if (request.target.equals(LinkNames.PROXY, ignoreCase = true)) {
            executor?.run(request, toOrigin) ?: toOrigin(RemoteResult(request.id, server, false, "Прокси не принимает действия.", Instant.now()))
            return
        }
        if (transport?.send(request.target, LinkCodec.encodeRequest(request, key)) != true) {
            toOrigin(RemoteResult(request.id, server, false, "Сервер ${request.target} недоступен.", Instant.now()))
        }
    }

    private fun deliver(result: RemoteResult, key: ByteArray) {
        if (result.origin == LinkNames.PROXY_ORIGIN) replies.complete(result) else transport?.send(result.origin, LinkCodec.encodeResult(result, key))
    }
}
