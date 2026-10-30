package org.pnfolder.pnlibrary.core.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * A question one server puts to another about its library, or a thing it asks it to do.
 *
 * @property id identifies the question and its answer
 * @property target the server that answers, or [LinkNames.PROXY]
 * @property origin the server that asks; the proxy fills it in from the connection the question came on
 * @property viewer the name of the person on whose behalf it is asked, for the update history
 * @property method what is asked
 * @property args the arguments, as JSON
 * @property sentAt when it was asked
 */
data class DataCall(
    val id: UUID,
    val target: String,
    val origin: String,
    val viewer: String,
    val method: String,
    val args: String,
    val sentAt: Instant,
)

/**
 * One piece of the answer to a [DataCall]. An answer can be longer than a plugin message, so it travels in pieces.
 *
 * @property id id of the question
 * @property to the server that asked
 * @property ok whether the answer is the data, or the reason there is none
 * @property index the place of this piece, from 0
 * @property count how many pieces the answer has
 * @property bytes the compressed text of this piece
 * @property sentAt when it was sent
 */
class DataChunk(
    val id: UUID,
    val to: String,
    val ok: Boolean,
    val index: Int,
    val count: Int,
    val bytes: ByteArray,
    val sentAt: Instant,
)

/** Answers the questions other servers ask of this one. */
fun interface DataHandler {
    /** Answers [call]; [reply] takes whether it worked and the answer, and may be called from any thread. */
    fun handle(call: DataCall, reply: (Boolean, String) -> Unit)
}

/** Why a question got no answer. */
class DataException(message: String) : RuntimeException(message)

/** Cuts an answer into pieces that fit a plugin message and puts it back together. */
object DataChunks {
    /** The most bytes of one piece. */
    const val PIECE = 24_000

    /** The most pieces of one answer. */
    const val MAX_PIECES = 80

    private const val MAX_INFLATED = 4_000_000
    private const val BUFFER = 4_096

    /** The signed messages that carry [text] to [to], or null when it is too long even for [MAX_PIECES]. */
    fun split(id: UUID, to: String, ok: Boolean, text: String, key: ByteArray, now: Instant = Instant.now()): List<ByteArray>? {
        val packed = ByteArrayOutputStream()
        DeflaterOutputStream(packed).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        val bytes = packed.toByteArray()
        val count = maxOf(1, (bytes.size + PIECE - 1) / PIECE)
        if (count > MAX_PIECES) return null
        return List(count) { index ->
            val piece = bytes.copyOfRange(index * PIECE, minOf(bytes.size, (index + 1) * PIECE))
            LinkCodec.encodeChunk(DataChunk(id, to, ok, index, count, piece, now), key)
        }
    }

    /** The text in [pieces], which must be all of them in order, or null when they do not hold one. */
    fun join(pieces: List<ByteArray>): String? = runCatching {
        val all = ByteArrayOutputStream().also { out -> pieces.forEach(out::write) }.toByteArray()
        InflaterInputStream(ByteArrayInputStream(all)).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > MAX_INFLATED) return null
            }
            String(out.toByteArray(), Charsets.UTF_8)
        }
    }.getOrNull()
}

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
