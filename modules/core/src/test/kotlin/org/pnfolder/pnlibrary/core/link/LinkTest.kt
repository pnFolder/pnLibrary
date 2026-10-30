package org.pnfolder.pnlibrary.core.link

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class LinkTest {
    private val key = ByteArray(32) { it.toByte() }
    private val report = LinkReport(
        "Paper 1.21", "0.0.1", 3,
        listOf(LinkPlugin("shop", "1.0.0", "1.1.0", "UPDATE_AVAILABLE"), LinkPlugin("pnlibrary", "0.0.1", null, "UP_TO_DATE")),
        Instant.now(),
    )

    @Test
    fun `a signed report survives the trip`() {
        val decoded = LinkCodec.decode(LinkCodec.encode(report, key), key)

        assertEquals(report.copy(sentAt = report.sentAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)), decoded)
    }

    @Test
    fun `a wrong key, a changed byte and an old message are all dropped`() {
        val message = LinkCodec.encode(report, key)

        assertNull(LinkCodec.decode(message, ByteArray(32) { 7 }))
        assertNull(LinkCodec.decode(message.copyOf().also { it[10] = (it[10] + 1).toByte() }, key))
        assertNull(LinkCodec.decode(message, key, Instant.now().plus(Duration.ofMinutes(10))))
        assertNull(LinkCodec.decode(ByteArray(3), key))
    }

    @Test
    fun `only a valid report from a server is remembered`() {
        val registry = LinkRegistry()
        val receiver = LinkReceiver(key, registry)
        val message = LinkCodec.encode(report, key)

        assertFalse(receiver.receive("lobby", fromServer = false, message = message))
        assertFalse(receiver.receive("lobby", fromServer = true, message = message.copyOf().also { it[0] = 9 }))
        assertTrue(registry.all().isEmpty())
        assertTrue(receiver.receive("lobby", fromServer = true, message = message))
        assertNotNull(registry.get("LOBBY"))
        assertFalse(LinkReceiver(null, registry).receive("lobby", true, message))
    }

    @Test
    fun `the proxy makes the key once and a server only reads it`(@TempDir folder: Path) {
        assertNull(LinkSecret.read(folder))
        val made = LinkSecret.readOrCreate(folder)

        assertNotNull(made)
        assertTrue(LinkSecret.read(folder)!!.contentEquals(made))
        assertTrue(LinkSecret.readOrCreate(folder)!!.contentEquals(made))
    }

    @Test
    fun `the network map keeps every server and drops forgeries`() {
        val network = NetworkReport(report, listOf(NetworkServer("lobby", true, report), NetworkServer("games", false, report)), report.sentAt)
        val message = LinkCodec.encodeNetwork(network, key)

        val decoded = LinkCodec.decodeNetwork(message, key)!!
        assertEquals(listOf("lobby", "games"), decoded.servers.map { it.name })
        assertEquals(listOf(true, false), decoded.servers.map { it.online })
        assertEquals(report.plugins, decoded.servers.first().report.plugins)
        assertNull(LinkCodec.decodeNetwork(message, ByteArray(32) { 7 }))
        assertNull(LinkCodec.decode(message, key))
        assertNull(LinkCodec.decodeNetwork(LinkCodec.encode(report, key), key))
    }

    private val request = RemoteRequest(java.util.UUID.randomUUID(), "games", "", "Admin", RemoteAction.UPDATE, "shop", Instant.now())

    @Test
    fun `requests and answers survive the trip and reject forgeries`() {
        val sent = LinkCodec.decodeRequest(LinkCodec.encodeRequest(request, key), key)!!
        assertEquals(request.id, sent.id)
        assertEquals(RemoteAction.UPDATE, sent.action)
        assertEquals("shop", sent.product)
        assertNull(LinkCodec.decodeRequest(LinkCodec.encodeRequest(request, key), ByteArray(32) { 7 }))

        val result = RemoteResult(request.id, "lobby", true, "Готово", Instant.now())
        val answer = LinkCodec.decodeResult(LinkCodec.encodeResult(result, key), key)!!
        assertEquals("Готово", answer.text)
        assertTrue(answer.ok)
        assertNull(LinkCodec.decodeRequest(LinkCodec.encodeResult(result, key), key))
    }

    @Test
    fun `the proxy passes a request to its target and the answer back to who asked`() {
        val sent = mutableListOf<Pair<String, ByteArray>>()
        val replies = RemoteReplies()
        val transport = LinkTransport { server, bytes ->
            sent += server to bytes
            server != "gone"
        }
        val receiver = LinkReceiver(key, LinkRegistry(), transport = transport, replies = replies)

        assertTrue(receiver.receive("lobby", true, LinkCodec.encodeRequest(request, key)))
        val forwarded = LinkCodec.decodeRequest(sent.single().second, key)!!
        assertEquals("games", sent.single().first)
        assertEquals("lobby", forwarded.origin)

        sent.clear()
        assertTrue(receiver.receive("games", true, LinkCodec.encodeResult(RemoteResult(request.id, "lobby", true, "ok", Instant.now()), key)))
        assertEquals("lobby", sent.single().first)

        sent.clear()
        receiver.receive("lobby", true, LinkCodec.encodeRequest(request.copy(target = "gone", id = java.util.UUID.randomUUID()), key))
        val failure = LinkCodec.decodeResult(sent.last().second, key)!!
        assertEquals("lobby", sent.last().first)
        assertFalse(failure.ok)
        assertFalse(receiver.receive("lobby", false, LinkCodec.encodeRequest(request, key)))
    }

    @Test
    fun `an answer to the proxy itself reaches whoever waits for it`() {
        val replies = RemoteReplies()
        var got: RemoteResult? = null
        replies.expect(request.id) { got = it }
        val receiver = LinkReceiver(key, LinkRegistry(), replies = replies)

        receiver.receive("games", true, LinkCodec.encodeResult(RemoteResult(request.id, LinkNames.PROXY_ORIGIN, true, "ok", Instant.now()), key))

        assertEquals("ok", got?.text)
    }
}
