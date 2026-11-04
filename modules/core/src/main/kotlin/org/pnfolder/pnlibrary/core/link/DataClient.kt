package org.pnfolder.pnlibrary.core.link

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The questions this server asked and has no answer to yet. */
class DataClient(private val timeoutSeconds: Long = TIMEOUT_SECONDS) {
    private class Pending(val future: CompletableFuture<String>) {
        val pieces = ConcurrentHashMap<Int, ByteArray>()
    }

    private val waiting = ConcurrentHashMap<UUID, Pending>()
    private val timer = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "pnlibrary-link-data").apply { isDaemon = true } }

    /**
     * Asks [target] for [method]. The future holds the answer, or fails with a [DataException] when the server is not
     * reachable, does not answer in time or refuses.
     */
    fun call(target: String, viewer: String, method: String, args: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        val key = LinkHub.key
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
        waiting[call.id] = Pending(future)
        val sent = LinkHub.uplink?.invoke(bytes) ?: false
        if (!sent) {
            waiting.remove(call.id)
            future.completeExceptionally(DataException("Некому передать запрос: на этом сервере нет игроков онлайн."))
            return future
        }
        timer.schedule({
            waiting.remove(call.id)?.future?.completeExceptionally(DataException("Сервер $target не ответил за $timeoutSeconds секунд."))
        }, timeoutSeconds, TimeUnit.SECONDS)
        return future
    }

    /** Takes one piece of an answer; completes the question when the last one arrives. */
    fun accept(chunk: DataChunk) {
        val pending = waiting[chunk.id] ?: return
        pending.pieces[chunk.index] = chunk.bytes
        if (pending.pieces.size < chunk.count) return
        if (!waiting.remove(chunk.id, pending)) return
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
