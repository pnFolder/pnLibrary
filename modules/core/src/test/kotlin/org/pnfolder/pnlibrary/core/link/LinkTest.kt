package org.pnfolder.pnlibrary.core.link

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
        val network = NetworkReport(
            report, listOf(NetworkServer("lobby", true, report), NetworkServer("games", false, report)), report.sentAt,
            listOf(ServerGroups.Group("grief", setOf("lobby"), setOf("shop"))),
        )
        val message = LinkCodec.encodeNetwork(network, key)

        val decoded = LinkCodec.decodeNetwork(message, key)!!
        assertEquals(listOf("lobby", "games"), decoded.servers.map { it.name })
        assertEquals(listOf(true, false), decoded.servers.map { it.online })
        assertEquals(report.plugins, decoded.servers.first().report.plugins)
        assertEquals(network.groups, decoded.groups)
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

    private val call = DataCall(java.util.UUID.randomUUID(), "games", "", "Admin", "snapshots", "{\"product\":\"shop\"}", Instant.now())

    @Test
    fun `a question survives the trip and rejects forgeries`() {
        val decoded = LinkCodec.decodeCall(LinkCodec.encodeCall(call, key), key)!!
        assertEquals("snapshots", decoded.method)
        assertEquals("{\"product\":\"shop\"}", decoded.args)
        assertNull(LinkCodec.decodeCall(LinkCodec.encodeCall(call, key), ByteArray(32) { 7 }))
        assertNull(LinkCodec.decodeChunk(LinkCodec.encodeCall(call, key), key))
    }

    @Test
    fun `a long answer travels in pieces and comes back whole`() {
        val random = java.util.Random(1)
        val text = (0 until 120_000).map { ('a' + random.nextInt(26)) }.joinToString("")
        val pieces = DataChunks.split(call.id, "lobby", true, text, key)!!

        assertTrue(pieces.size > 1)
        assertTrue(pieces.all { it.size <= LinkCodec.MAX_FRAME_BYTES })
        val chunks = pieces.map { LinkCodec.decodeChunk(it, key)!! }
        assertEquals(text, DataChunks.join(chunks.sortedBy { it.index }.map { it.bytes }))
        assertNull(LinkCodec.decodeChunk(pieces.first(), ByteArray(32) { 7 }))
    }

    @Test
    fun `the asker gets the answer when the last piece arrives and an error when the server refuses`() {
        val asked = mutableListOf<DataCall>()
        LinkHub.key = key
        LinkHub.uplink = { bytes -> asked.add(LinkCodec.decodeCall(bytes, key)!!); true }
        try {
            val client = DataClient()
            val good = client.call("games", "Admin", "version", "{}")
            val bad = client.call("games", "Admin", "plan", "{}")
            assertFalse(good.isDone)

            val text = "x".repeat(100_000)
            val pieces = DataChunks.split(asked[0].id, "lobby", true, text, key)!!.map { LinkCodec.decodeChunk(it, key)!! }
            pieces.dropLast(1).forEach(client::accept)
            assertFalse(good.isDone)
            client.accept(pieces.last())
            assertEquals(text, good.get())

            DataChunks.split(asked[1].id, "lobby", false, "нет доступа", key)!!.forEach { client.accept(LinkCodec.decodeChunk(it, key)!!) }
            assertTrue(bad.isCompletedExceptionally)
            assertEquals("нет доступа", assertThrows(java.util.concurrent.ExecutionException::class.java) { bad.get() }.cause?.message)
        } finally {
            LinkHub.uplink = null
            LinkHub.key = null
        }
    }

    @Test
    fun `the proxy passes a question on, takes the answer back and tells the asker when the server is gone`() {
        val sent = mutableListOf<Pair<String, ByteArray>>()
        val receiver = LinkReceiver(key, LinkRegistry(), transport = LinkTransport { server, bytes -> sent.add(server to bytes); server != "gone" })

        assertTrue(receiver.receive("lobby", true, LinkCodec.encodeCall(call, key)))
        assertEquals("games", sent.single().first)
        assertEquals("lobby", LinkCodec.decodeCall(sent.single().second, key)!!.origin)

        sent.clear()
        val answer = DataChunks.split(call.id, "lobby", true, "[]", key)!!.single()
        assertTrue(receiver.receive("games", true, answer))
        assertEquals("lobby", sent.single().first)

        sent.clear()
        receiver.receive("lobby", true, LinkCodec.encodeCall(call.copy(target = "gone"), key))
        val refused = LinkCodec.decodeChunk(sent.last().second, key)!!
        assertEquals("lobby", sent.last().first)
        assertFalse(refused.ok)
        assertFalse(receiver.receive("lobby", false, LinkCodec.encodeCall(call, key)))
    }

    @Test
    fun `the proxy answers a question for itself through its handler`() {
        val sent = mutableListOf<Pair<String, ByteArray>>()
        val receiver = LinkReceiver(
            key, LinkRegistry(), transport = LinkTransport { server, bytes -> sent.add(server to bytes); true },
            data = DataHandler { question, reply -> reply(true, "answer to ${question.method}") },
        )

        receiver.receive("lobby", true, LinkCodec.encodeCall(call.copy(target = LinkNames.PROXY), key))

        val chunk = LinkCodec.decodeChunk(sent.single().second, key)!!
        assertEquals("lobby", sent.single().first)
        assertEquals("answer to snapshots", DataChunks.join(listOf(chunk.bytes)))
    }
}
