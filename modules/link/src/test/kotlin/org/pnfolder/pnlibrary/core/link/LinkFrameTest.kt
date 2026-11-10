package org.pnfolder.pnlibrary.core.link

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac

class LinkFrameTest {
    private val key = ByteArray(32) { it.toByte() }
    private val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    private val id = UUID(1, 2)
    private val report = LinkReport("Paper", "0.1.0", 4, listOf(LinkPlugin("shop", "1.0", "1.1", "UPDATE_AVAILABLE")), now)
    private val request = RemoteRequest(id, "lobby", "survival", "admin", RemoteAction.CHECK, null, now)
    private val result = RemoteResult(id, "survival", true, "done", now)
    private val call = DataCall(id, "lobby", "survival", "admin", "plugins", "{}", now)
    private val chunk = DataChunk(id, "survival", true, 0, 1, byteArrayOf(1, 2, 3), now)

    private fun valid(): List<ByteArray> = listOf(
        LinkCodec.encode(report, key), LinkCodec.encodeRequest(request, key), LinkCodec.encodeResult(result, key),
        LinkCodec.encodeCall(call, key), LinkCodec.encodeChunk(chunk, key),
    )

    private fun show(value: Any?): String = when (value) {
        null -> "null"
        is DataChunk -> "chunk(${value.id},${value.to},${value.ok},${value.index},${value.count},${value.bytes.toList()},${value.sentAt})"
        else -> value.toString()
    }

    /** The receiver's previous chain: one signature check per decoder until one accepts. */
    private fun chain(message: ByteArray, at: Instant): String = show(
        LinkCodec.decode(message, key, at) ?: LinkCodec.decodeRequest(message, key, at) ?: LinkCodec.decodeResult(message, key, at)
            ?: LinkCodec.decodeCall(message, key, at) ?: LinkCodec.decodeChunk(message, key, at),
    )

    private fun frame(message: ByteArray, at: Instant, signer: FrameSigner = CachedHmac()): String = show(
        when (val decoded = LinkCodec.decodeFrame(message, key, at, signer)) {
            null -> null
            is LinkFrame.Report -> decoded.report
            is LinkFrame.Request -> decoded.request
            is LinkFrame.Result -> decoded.result
            is LinkFrame.Call -> decoded.call
            is LinkFrame.Chunk -> decoded.chunk
        },
    )

    /** Valid frames of every kind, forged, foreign, stale, cut and extended ones. */
    private fun corpus(): List<ByteArray> {
        val frames = valid()
        val other = ByteArray(32) { 9 }
        return frames + frames.flatMap { message ->
            listOf(
                message.copyOf().also { it[0] = (it[0] + 1).toByte() },
                message.copyOf().also { it[5] = (it[5] + 1).toByte() },
                message.copyOf().also { it[message.size - 1] = (it[message.size - 1] + 1).toByte() },
                message.copyOf(message.size - 1), message.copyOf(40), message + byteArrayOf(0), byteArrayOf(),
            )
        } + listOf(
            LinkCodec.encode(report, other), LinkCodec.encodeRequest(request, other), LinkCodec.encodeChunk(chunk, other),
            LinkCodec.encodeNetwork(NetworkReport(report, emptyList(), now, emptyList()), key),
            LinkCodec.encodeChunk(DataChunk(id, "x", true, 5, 2, byteArrayOf(), now), key),
        )
    }

    @Test
    fun `one frame decoder answers exactly as the chain of five decoders did`() {
        listOf(now, now.plus(Duration.ofMinutes(10)), now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofMinutes(1))).forEach { at ->
            corpus().forEachIndexed { index, message -> assertEquals(chain(message, at), frame(message, at), "frame $index at $at") }
        }
        valid().forEach { assertNotNull(LinkCodec.decodeFrame(it, key, now)) }
        assertNull(LinkCodec.decodeFrame(valid()[0], ByteArray(32) { 7 }, now))
    }

    @Test
    fun `a frame of any kind costs one signature check where the chain cost up to five`() {
        val checks = AtomicInteger()
        val counting = FrameSigner { bytes, signKey -> checks.incrementAndGet(); CachedHmac().sign(bytes, signKey) }
        valid().forEach { message ->
            checks.set(0)
            assertNotNull(LinkCodec.decodeFrame(message, key, now, counting))
            assertEquals(1, checks.get())
        }
    }

    @Test
    fun `a thread gets one Mac per key however many frames it signs`() {
        val created = AtomicInteger()
        val signer = CachedHmac { created.incrementAndGet(); Mac.getInstance("HmacSHA256") }
        val message = ByteArray(100) { it.toByte() }
        val expected = CachedHmac().sign(message, key)
        repeat(100) { assertEquals(expected.toList(), signer.sign(message, key).toList()) }
        assertEquals(1, created.get())
        signer.sign(message, ByteArray(32) { 5 })
        assertEquals(2, created.get())
    }
}
