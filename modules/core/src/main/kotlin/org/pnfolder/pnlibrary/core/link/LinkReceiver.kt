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
    private val data: DataHandler? = null,
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
        LinkCodec.decodeCall(message, key)?.let {
            routeCall(server, it.copy(origin = server), key)
            return true
        }
        LinkCodec.decodeChunk(message, key)?.let {
            if (it.to == LinkNames.PROXY_ORIGIN) LinkHub.data.accept(it) else transport?.send(it.to, message)
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

    /** Passes a question on to the server it is for, answers it here when it is for the proxy, or tells the asker why it cannot. */
    private fun routeCall(server: String, call: DataCall, key: ByteArray) {
        if (call.target.equals(LinkNames.PROXY, ignoreCase = true)) {
            val handler = data ?: return refuse(server, call, key, "Прокси не отвечает на запросы меню.")
            handler.handle(call) { ok, text -> answer(server, call.id, ok, text, key) }
            return
        }
        if (transport?.send(call.target, LinkCodec.encodeCall(call, key)) != true) refuse(server, call, key, "Сервер ${call.target} недоступен.")
    }

    private fun refuse(server: String, call: DataCall, key: ByteArray, text: String) = answer(server, call.id, false, text, key)

    private fun answer(server: String, id: java.util.UUID, ok: Boolean, text: String, key: ByteArray) {
        val pieces = DataChunks.split(id, server, ok, text, key) ?: DataChunks.split(id, server, false, "Ответ слишком большой.", key).orEmpty()
        pieces.forEach { transport?.send(server, it) }
    }

    private fun deliver(result: RemoteResult, key: ByteArray) {
        if (result.origin == LinkNames.PROXY_ORIGIN) replies.complete(result) else transport?.send(result.origin, LinkCodec.encodeResult(result, key))
    }
}
