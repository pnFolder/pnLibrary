package org.pnfolder.pnlibrary.core.link

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Base64

class LinkLimitsTest {
    private val key = ByteArray(32) { it.toByte() }

    private fun report(plugins: Int) = LinkReport("Paper", "0.1.0", 1,
        List(plugins) { LinkPlugin("p".repeat(150) + it, "1.0.0", "2.0.0", "UPDATE_AVAILABLE") }, Instant.now())

    @Test
    fun `a huge network map is cut to the frame limit and still verifies`() {
        val network = NetworkReport(report(40),
            List(30) { NetworkServer("s$it", true, report(40)) }, Instant.now(),
            List(30) { ServerGroups.Group("g$it", List(60) { n -> "server".repeat(30) + n }.toSet(), List(60) { n -> "plugin".repeat(30) + n }.toSet()) })

        val message = LinkCodec.encodeNetwork(network, key)

        assertTrue(message.size <= LinkCodec.MAX_FRAME_BYTES, "size ${message.size}")
        assertNotNull(LinkCodec.decodeNetwork(message, key))
        assertNull(LinkCodec.decodeNetwork(message, ByteArray(32) { 9 }))
    }

    @Test
    fun `any changed byte of a signed frame is rejected`() {
        val message = LinkCodec.encode(report(2), key)
        for (index in message.indices step 7) {
            val forged = message.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertNull(LinkCodec.decode(forged, key), "byte $index")
        }
        assertNull(LinkCodec.decode(message + byteArrayOf(0), key))
    }

    @Test
    fun `an existing unreadable key file is kept and not replaced`(@TempDir folder: Path) {
        val file = folder.resolve(LinkSecret.FILE)
        Files.writeString(file, "not a key !!")
        val warnings = mutableListOf<String>()

        assertNull(LinkSecret.readOrCreate(folder) { warnings += it })

        assertEquals("not a key !!", Files.readString(file))
        assertEquals(1, warnings.size)
    }

    @Test
    fun `a key file with a BOM and a line break is read`(@TempDir folder: Path) {
        val key = ByteArray(32) { (it * 3).toByte() }
        Files.write(folder.resolve(LinkSecret.FILE), ("﻿" + Base64.getEncoder().encodeToString(key) + "\r\n").toByteArray(Charsets.UTF_8))

        assertArrayEquals(key, LinkSecret.readOrCreate(folder))
    }

    @Test
    fun `a missing key file is created in a missing folder`(@TempDir folder: Path) {
        val nested = folder.resolve("a/b")

        val made = LinkSecret.readOrCreate(nested)

        assertNotNull(made)
        assertTrue(Files.exists(nested.resolve(LinkSecret.FILE)))
    }
}
