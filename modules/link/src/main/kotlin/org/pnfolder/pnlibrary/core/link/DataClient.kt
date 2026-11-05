package org.pnfolder.pnlibrary.core.link

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

/**
 * The questions this server asked and has no answer to yet.
 *
 * @param key the shared key, or null when the link is off
 * @param uplink sends a question to the proxy; false when nobody can carry it
 */
class DataClient(
    private val key: ByteArray?,
    private val timeoutSeconds: Long = TIMEOUT_SECONDS,
    private val uplink: (ByteArray) -> Boolean,
) {
    private class Pending(val future: CompletableFuture<String>) {
        val pieces = ConcurrentHashMap<Int, ByteArray>()

        @Volatile var timeout: ScheduledFuture<*>? = null
    }

    private val waiting = ConcurrentHashMap<UUID, Pending>()
    private val timeouts = LinkTimeouts("pnlibrary-link-data")

    /**
     * Asks [target] for [method]. The future holds the answer, or fails with a [DataException] when the server is not
     * reachable, does not answer in time or refuses.
     */
    fun call(target: String, viewer: String, method: String, args: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        if (key == null) {
            future.completeExceptionally(DataException("Связь выключена: нет файла link.key."))
            return future
        }
        val call = DataCall(UUID.randomUUID(), target, "", viewer, method, args, Instant.now())
        val bytes = LinkCodec.encodeCall(call, key)
        if (bytes.size > LinkCodec.MAX_FRAME_BYTES) {
            future.completeExceptionally(DataException("Запрос слишком длинный."))
            return future
        }
        val pending = Pending(future)
        waiting[call.id] = pending
        if (!uplink(bytes)) {
            waiting.remove(call.id)
            future.completeExceptionally(DataException("Некому передать запрос: на этом сервере нет игроков онлайн."))
            return future
        }
        pending.timeout = timeouts.after(timeoutSeconds) {
            if (waiting.remove(call.id, pending)) fail(pending, "Сервер $target не ответил за $timeoutSeconds секунд.")
        }
        return future
    }

    /** Takes one piece of an answer; completes the question when the last one arrives. */
    fun accept(chunk: DataChunk) {
        val pending = waiting[chunk.id] ?: return
        pending.pieces[chunk.index] = chunk.bytes
        if (pending.pieces.size < chunk.count) return
        if (!waiting.remove(chunk.id, pending)) return
        pending.timeout?.cancel(false)
        val pieces = (0 until chunk.count).map { pending.pieces[it] }
        if (pieces.any { it == null }) return fail(pending, "Ответ неполный.")
        val text = DataChunks.join(pieces.filterNotNull())
        when {
            text == null -> fail(pending, "Ответ не удалось прочитать.")
            chunk.ok -> pending.future.complete(text)
            else -> fail(pending, text)
        }
    }

    private fun fail(pending: Pending, text: String) {
        pending.future.completeExceptionally(DataException(text))
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
    }
}
