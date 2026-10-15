package ru.privatenull.pnlibrary.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executor

class ReleaseCatalogClientTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `loads catalog and reuses fresh cache`() {
        val source = URI.create("https://example.org/.pnlibrary/releases.json")
        var calls = 0
        val http = object : TrustedHttpClient(Duration.ZERO, Duration.ZERO, emptySet()) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray {
                calls++
                return """
                    {"schema":1,"product":"demo","releases":[{"version":"1.0.0",
                    "channel":"stable","description":"Demo","publishedAt":"2026-09-29T12:00:00Z",
                    "api":{"minimum":1,"maximum":1},"artifacts":[{"file":"demo.jar",
                    "platform":"VELOCITY","platformApi":{"minimum":"3.3.0"},
                    "java":{"minimum":17},"url":"https://example.org/demo.jar"}]}]}
                """.trimIndent().toByteArray()
            }
        }
        val client = ReleaseCatalogClient(http, ReleaseCatalogueStore(directory), Executor { it.run() }, Duration.ofHours(1))
        assertEquals("1.0.0", client.load(source).join().releases.single().version.toString())
        assertEquals("1.0.0", client.load(source).join().releases.single().version.toString())
        assertEquals(1, calls)
    }
}
