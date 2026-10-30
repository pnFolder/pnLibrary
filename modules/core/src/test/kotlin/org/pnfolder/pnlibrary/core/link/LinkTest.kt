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
}
