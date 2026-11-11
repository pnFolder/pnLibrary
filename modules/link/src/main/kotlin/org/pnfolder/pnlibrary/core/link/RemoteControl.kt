package org.pnfolder.pnlibrary.core.link

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

/** Sends bytes to the game server called [server]; returns false when it cannot be reached. */
fun interface LinkTransport {
    fun send(server: String, message: ByteArray): Boolean
}

/** The answers still awaited for requests this server made. */
class RemoteReplies {
    private class Waiting(val onResult: (RemoteResult) -> Unit) {
        @Volatile var timeout: ScheduledFuture<*>? = null
    }

    private val waiting = ConcurrentHashMap<UUID, Waiting>()
    private val timeouts = LinkTimeouts("pnlibrary-link-replies")

    /** Waits for the answer to request [id]; if none comes in time, [onResult] gets a failure instead. */
    fun expect(id: UUID, onResult: (RemoteResult) -> Unit) {
        val entry = Waiting(onResult)
        waiting[id] = entry
        entry.timeout = timeouts.after(TIMEOUT_SECONDS) {
            if (waiting.remove(id, entry)) onResult(RemoteResult(id, "", false, "Сервер не ответил за $TIMEOUT_SECONDS секунд.", Instant.now()))
        }
    }

    fun cancel(id: UUID) {
        waiting.remove(id)?.timeout?.cancel(false)
    }

    /** Hands [result] to whoever waits for it; returns false when nobody does. */
    fun complete(result: RemoteResult): Boolean {
        val entry = waiting.remove(result.id) ?: return false
        entry.timeout?.cancel(false)
        entry.onResult(result)
        return true
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
    }
}

/** Sends update controls to another server, from a game server through the proxy or from the proxy itself. */
class RemoteControl(private val link: Link) {
    /**
     * Asks [target] to do [action]. [onResult] gets the answer, or a failure after a timeout.
     *
     * @return null when the request was sent, otherwise why it could not be
     */
    fun send(target: String, action: RemoteAction, product: String?, actor: String, onResult: (RemoteResult) -> Unit): String? {
        val key = link.key ?: return "Связь выключена: нет файла link.key."
        if (action.needsProduct && product.isNullOrBlank()) return "Нужно указать плагин."
        val fromProxy = link.onProxy
        val request = RemoteRequest(
            UUID.randomUUID(), target, if (fromProxy) LinkNames.PROXY_ORIGIN else "", actor, action, product?.takeIf { it.isNotBlank() }, Instant.now(),
        )
        val bytes = LinkCodec.encodeRequest(request, key)
        link.replies.expect(request.id, onResult)
        if (!link.send(target, bytes)) {
            link.replies.cancel(request.id)
            return if (fromProxy) "Сервер $target недоступен." else "Некому передать запрос: на сервере нет игроков онлайн."
        }
        return null
    }
}
