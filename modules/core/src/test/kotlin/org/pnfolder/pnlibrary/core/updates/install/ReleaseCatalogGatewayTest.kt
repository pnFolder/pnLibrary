package org.pnfolder.pnlibrary.core.updates.install

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.core.updates.engine.RefreshMode
import org.pnfolder.pnlibrary.core.updates.engine.ReleaseCatalog
import org.pnfolder.pnlibrary.core.updates.engine.ReleaseCatalogClient
import org.pnfolder.pnlibrary.core.updates.engine.ReleaseCatalogStore
import org.pnfolder.pnlibrary.core.updates.engine.TrustedHttpClient
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class ReleaseCatalogGatewayTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `single request thread does not deadlock while catalog client uses its own executor`() {
        val catalogExecutor = Executors.newSingleThreadExecutor()
        val requestExecutor = Executors.newSingleThreadExecutor()
        val catalogBytes = catalogJson().toByteArray()
        val http = object : TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray =
                if (uri.host == "api.github.com") {
                    """{"content":"${Base64.getEncoder().encodeToString(catalogBytes)}"}""".toByteArray()
                } else {
                    catalogBytes
                }
        }
        val client = ReleaseCatalogClient(
            http = http,
            store = ReleaseCatalogStore(temporaryDirectory.resolve("catalog")),
            executor = catalogExecutor,
            ttl = Duration.ofMinutes(30),
        )
        val gateway = ReleaseCatalogGateway(client, http, requestExecutor)

        try {
            val loaded = AtomicReference<ReleaseCatalog>()
            assertTimeoutPreemptively(Duration.ofSeconds(3)) {
                loaded.set(gateway.load("owner", "example", RefreshMode.FORCE_REMOTE))
            }
            val catalog = loaded.get()
            assertEquals("example", catalog.product)
            assertEquals("1.0.0", catalog.releases.single().version.toString())
        } finally {
            requestExecutor.shutdownNow()
            catalogExecutor.shutdownNow()
        }
    }

    private fun catalogJson(): String = """
        {
          "schema": 1,
          "product": "example",
          "releases": [
            {
              "version": "1.0.0",
              "channel": "stable",
              "description": "test",
              "publishedAt": "2026-10-03T00:00:00Z",
              "api": { "minimum": 1, "maximum": 1 },
              "artifacts": [
                {
                  "file": "example-1.0.0-bukkit-java8.jar",
                  "platform": "BUKKIT",
                  "java": { "minimum": 8 },
                  "url": "https://example.com/example.jar"
                }
              ]
            }
          ]
        }
    """.trimIndent()
}
