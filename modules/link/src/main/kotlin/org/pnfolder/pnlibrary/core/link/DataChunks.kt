package org.pnfolder.pnlibrary.core.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

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
