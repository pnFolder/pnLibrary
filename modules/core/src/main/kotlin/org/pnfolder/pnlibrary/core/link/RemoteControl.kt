package org.pnfolder.pnlibrary.core.link

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Sends bytes to the game server called [server]; returns false when it cannot be reached. */
fun interface LinkTransport {
    /** Sends [message] to [server]. */
    fun send(server: String, message: ByteArray): Boolean
}

/** The answers still awaited for requests this server made. */
class RemoteReplies {
    private val waiting = ConcurrentHashMap<UUID, (RemoteResult) -> Unit>()
    private val timer = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "pnlibrary-link-replies").apply { isDaemon = true } }

    /** Waits for the answer to request [id]; if none comes in time, [onResult] gets a failure instead. */
    fun expect(id: UUID, onResult: (RemoteResult) -> Unit) {
        waiting[id] = onResult
        timer.schedule({
            waiting.remove(id)?.invoke(RemoteResult(id, "", false, "Сервер не ответил за $TIMEOUT_SECONDS секунд.", Instant.now()))
        }, TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /** Stops waiting for [id]. */
    fun cancel(id: UUID) {
        waiting.remove(id)
    }

    /** Hands [result] to whoever waits for it; returns false when nobody does. */
    fun complete(result: RemoteResult): Boolean {
        val handler = waiting.remove(result.id) ?: return false
        handler(result)
        return true
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
    }
}

/** Sends update controls to another server, from a game server through the proxy or from the proxy itself. */
object RemoteControl {
    /**
     * Asks [target] to do [action]. [onResult] gets the answer, or a failure after a timeout.
     *
     * @return null when the request was sent, otherwise why it could not be
     */
    fun send(target: String, action: RemoteAction, product: String?, actor: String, onResult: (RemoteResult) -> Unit): String? {
        val key = LinkHub.key ?: return "Связь выключена: нет файла link.key."
        if (action.needsProduct && product.isNullOrBlank()) return "Нужно указать плагин."
        val fromProxy = LinkHub.transport != null
        val request = RemoteRequest(
            UUID.randomUUID(), target, if (fromProxy) LinkNames.PROXY_ORIGIN else "", actor, action, product?.takeIf { it.isNotBlank() }, Instant.now(),
        )
        val bytes = LinkCodec.encodeRequest(request, key)
        LinkHub.replies.expect(request.id, onResult)
        val sent = LinkHub.transport?.send(target, bytes) ?: LinkHub.uplink?.invoke(bytes) ?: false
        if (!sent) {
            LinkHub.replies.cancel(request.id)
            return if (fromProxy) "Сервер $target недоступен." else "Некому передать запрос: на сервере нет игроков онлайн."
        }
        return null
    }
}
