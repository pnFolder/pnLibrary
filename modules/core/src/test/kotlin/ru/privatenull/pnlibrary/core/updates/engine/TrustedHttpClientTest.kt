package ru.privatenull.pnlibrary.core.updates.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Duration

class TrustedHttpClientTest {
    private val client = TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1))

    @Test
    fun `construction leaves JVM networking properties untouched`() {
        val keys = listOf("java.net.preferIPv4Stack", "java.net.preferIPv4Addresses")
        val before = keys.associateWith(System::getProperty)

        TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1))

        assertEquals(before, keys.associateWith(System::getProperty))
    }

    @Test
    fun `rejects non-HTTPS and credential-bearing URLs`() {
        assertThrows<IllegalArgumentException> { client.validate(URI("http://example.org/file")) }
        assertThrows<IllegalArgumentException> { client.validate(URI("https://user@example.org/file")) }
    }

    @Test
    fun `bounded read rejects oversized bodies`() {
        assertArrayEquals(byteArrayOf(1, 2), client.readBounded(ByteArrayInputStream(byteArrayOf(1, 2)), 2))
        assertThrows<IllegalArgumentException> { client.readBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2) }
    }
}
