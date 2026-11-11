package org.pnfolder.pnlibrary.core.updates.install

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.updates.CatalogSource
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
import java.util.concurrent.TimeUnit
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
            // A real deadlock hangs forever; a generous bound only guards against that, not against a loaded CI
            // runner's thread-scheduling jitter across the two single-thread executors used here.
            assertTimeoutPreemptively(Duration.ofSeconds(15)) {
                loaded.set(gateway.load(CatalogSource("owner", "example"), RefreshMode.FORCE_REMOTE))
            }
            val catalog = loaded.get()
            assertEquals("example", catalog.product)
            assertEquals("1.0.0", catalog.releases.single().version.toString())
        } finally {
            requestExecutor.shutdownNow()
            catalogExecutor.shutdownNow()
            // The catalog client may still be writing its cache file; JUnit deletes the temporary directory next,
            // so wait until both threads have actually stopped instead of racing that cleanup.
            requestExecutor.awaitTermination(10, TimeUnit.SECONDS)
            catalogExecutor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `reads the catalog from the requested branch and folder and names the address when it is missing`() {
        val executor = Executors.newFixedThreadPool(4)
        val requested = java.util.concurrent.CopyOnWriteArrayList<String>()
        val http = object : TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray {
                requested += uri.toString()
                throw java.io.IOException("HTTP 404")
            }
        }
        val client = ReleaseCatalogClient(http, ReleaseCatalogStore(temporaryDirectory.resolve("branch")), executor, Duration.ofMinutes(30))
        try {
            val error = org.junit.jupiter.api.Assertions.assertThrows(Exception::class.java) {
                ReleaseCatalogGateway(client, http, executor).load(CatalogSource("PnFolder", "pnCases", "master", "updates"), RefreshMode.FORCE_REMOTE)
            }
            val raw = "https://raw.githubusercontent.com/PnFolder/pnCases/refs/heads/master/updates/releases.json"
            org.junit.jupiter.api.Assertions.assertTrue(requested.contains(raw), requested.toString())
            org.junit.jupiter.api.Assertions.assertTrue(generateSequence(error as Throwable) { it.cause }.any { it.message?.contains(raw) == true })
        } finally {
            executor.shutdownNow()
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
