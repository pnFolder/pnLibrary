package org.pnfolder.pnlibrary.core.downloads.fetch

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.downloads.DirectDownloadSource
import org.pnfolder.pnlibrary.common.io.Digests
import org.pnfolder.pnlibrary.core.downloads.settings.DownloadPins
import org.pnfolder.pnlibrary.core.updates.engine.net.TrustedHttpClient
import java.net.URI
import java.nio.file.Path
import java.time.Duration

class SourceFetcherTest {
    @TempDir lateinit var directory: Path

    private val first = "https://a.example.org/x.jar"
    private val second = "https://b.example.org/x.jar"

    private fun client(answers: MutableMap<String, ByteArray>) =
        object : TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray = answers[uri.toString()] ?: error("нет ответа")
        }

    private fun fetcher(answers: MutableMap<String, ByteArray>) = SourceFetcher(client(answers), DownloadPins(directory))

    private fun source(vararg mirrors: String, sha: String? = null) = DirectDownloadSource.builder().url(first)
        .also { builder -> mirrors.forEach(builder::mirror) }
        .also { builder -> if (sha != null) builder.integrity(4, sha) }
        .build()

    @Test
    fun `identical copies from several addresses are accepted`() {
        val bytes = "jar1".toByteArray()

        val result = fetcher(mutableMapOf(first to bytes, second to bytes)).fetch(source(second), 100)

        assertArrayEquals(bytes, result)
    }

    @Test
    fun `different copies are refused so a replaced file is caught`() {
        val answers = mutableMapOf(first to "jar1".toByteArray(), second to "evil".toByteArray())

        val error = assertThrows(IllegalArgumentException::class.java) { fetcher(answers).fetch(source(second), 100) }

        assertTrue(error.message!!.contains("разные файлы"))
    }

    @Test
    fun `a mirror answers when the first address is down`() {
        val result = fetcher(mutableMapOf(second to "jar1".toByteArray())).fetch(source(second), 100)

        assertEquals("jar1", String(result))
    }

    @Test
    fun `a single address that changes its file is refused until it is forgotten`() {
        val answers = mutableMapOf(first to "jar1".toByteArray())
        val pins = DownloadPins(directory)
        val fetcher = SourceFetcher(client(answers), pins)
        fetcher.fetch(source(), 100)
        answers[first] = "evil".toByteArray()

        assertThrows(IllegalArgumentException::class.java) { fetcher.fetch(source(), 100) }

        pins.forget(listOf(URI.create(first)))
        assertEquals("evil", String(fetcher.fetch(source(), 100)))
    }

    @Test
    fun `with a checksum the first matching address wins`() {
        val good = "jar1".toByteArray()
        val answers = mutableMapOf(first to "evil".toByteArray(), second to good)

        val result = fetcher(answers).fetch(source(second, sha = Digests.sha256(good)), 100)

        assertArrayEquals(good, result)
    }
}
