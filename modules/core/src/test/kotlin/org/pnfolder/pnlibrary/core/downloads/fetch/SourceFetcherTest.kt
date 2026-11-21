package org.pnfolder.pnlibrary.core.downloads.fetch

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.downloads.DirectDownloadSource
import org.pnfolder.pnlibrary.common.io.Digests
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient
import java.net.URI
import java.time.Duration

class SourceFetcherTest {
    private val first = "https://a.example.org/x.jar"
    private val second = "https://b.example.org/x.jar"

    private fun client(answers: MutableMap<String, ByteArray>) =
        object : TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray = answers[uri.toString()] ?: error("нет ответа")
        }

    private fun source(vararg mirrors: String, sha: String? = null) = DirectDownloadSource.builder().url(first)
        .also { builder -> mirrors.forEach(builder::mirror) }
        .also { builder -> if (sha != null) builder.sha256(sha) }
        .build()

    @Test
    fun `without a checksum the first address that answers is used and nothing is compared`() {
        val answers = mutableMapOf(first to "jar1".toByteArray(), second to "other".toByteArray())

        assertArrayEquals("jar1".toByteArray(), SourceFetcher(client(answers)).fetch(source(second), 100))
    }

    @Test
    fun `a mirror answers when the first address is down`() {
        val result = SourceFetcher(client(mutableMapOf(second to "jar1".toByteArray()))).fetch(source(second), 100)

        assertEquals("jar1", String(result))
    }

    @Test
    fun `with a checksum only the file that matches is taken`() {
        val good = "jar1".toByteArray()
        val answers = mutableMapOf(first to "evil".toByteArray(), second to good)

        val result = SourceFetcher(client(answers)).fetch(source(second, sha = Digests.sha256(good)), 100)

        assertArrayEquals(good, result)
    }

    @Test
    fun `with a checksum nothing is taken when no file matches`() {
        val answers = mutableMapOf(first to "evil".toByteArray(), second to "worse".toByteArray())

        assertThrows(IllegalArgumentException::class.java) {
            SourceFetcher(client(answers)).fetch(source(second, sha = Digests.sha256("jar1".toByteArray())), 100)
        }
    }
}
