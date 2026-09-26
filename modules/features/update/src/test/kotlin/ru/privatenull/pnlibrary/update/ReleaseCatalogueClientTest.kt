package ru.privatenull.pnlibrary.update

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.platform.PlatformType
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.io.ByteArrayInputStream

class ReleaseCatalogueClientTest {
    @TempDir lateinit var directory: Path

    @Test fun `coalesces concurrent catalogue loads and reuses verified disk cache offline`() {
        val transport = FixtureTransport()
        val source = ReleaseSource("pnFolder", "Economy")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val client = ReleaseCatalogueClient(transport, ReleaseCatalogueStore(directory), executor, Duration.ofHours(1))
            val first = client.releases(source, UpdateChannel.STABLE)
            val second = client.releases(source, UpdateChannel.STABLE)
            assertEquals("3.4.0", first.toCompletableFuture().join().single().version.toString())
            assertEquals(first.toCompletableFuture().join(), second.toCompletableFuture().join())
            assertEquals(1, transport.calls[transport.releasesUri])
            assertEquals(1, transport.calls[transport.manifestUri])

            val offline = ReleaseCatalogueClient(FailingTransport(), ReleaseCatalogueStore(directory), executor, Duration.ofHours(1))
            assertEquals("3.4.0", offline.releases(source, UpdateChannel.STABLE).toCompletableFuture().join().single().version.toString())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `quarantines structurally corrupt cached manifest and refetches it`() {
        val transport = FixtureTransport()
        val store = ReleaseCatalogueStore(directory)
        val executor = Executors.newSingleThreadExecutor()
        try {
            ReleaseCatalogueClient(transport, store, executor, Duration.ofHours(1))
                .releases(ReleaseSource("pnFolder", "Economy"), UpdateChannel.STABLE).toCompletableFuture().join()
            Files.write(store.dataPath(transport.manifestUri), "{}".toByteArray())
            store.rewriteDigest(transport.manifestUri)
            val refreshed = ReleaseCatalogueClient(transport, store, executor, Duration.ofHours(1))
                .releases(ReleaseSource("pnFolder", "Economy"), UpdateChannel.STABLE).toCompletableFuture().join()
            assertEquals("3.4.0", refreshed.single().version.toString())
            assertEquals(2, transport.calls[transport.manifestUri])
            assertTrue(Files.list(store.dataPath(transport.manifestUri).parent).use { files ->
                files.anyMatch { ".corrupt-" in it.fileName.toString() }
            })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun `trusted client rejects insecure and untrusted redirect targets`() {
        val client = TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1), setOf("api.github.com"))
        assertThrows(IllegalArgumentException::class.java) { client.validate(URI.create("http://api.github.com/repos/x/y")) }
        assertEquals("evil.example", client.validate(URI.create("https://evil.example/file")).host)
        assertEquals("api.github.com", client.validate(URI.create("https://api.github.com/repos/x/y")).host)
        assertThrows(IllegalArgumentException::class.java) {
            client.readBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3)
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), client.readBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3))
    }

    @Test fun `uses GitHub asset digest when release manifest is absent`() {
        val digest = "a".repeat(64)
        val releases = URI.create("https://api.github.com/repos/pnFolder/Cases/releases?per_page=30")
        val transport = object : TrustedHttpClient(Duration.ZERO, Duration.ZERO, emptySet()) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray {
                assertEquals(releases, uri)
                return """[{"tag_name":"v2.5.6","draft":false,"prerelease":false,"assets":[{
                    "name":"pnCases-Bukkit-2.5.6.jar","size":123,"digest":"sha256:$digest",
                    "browser_download_url":"https://github.com/pnFolder/Cases/releases/download/v2.5.6/pnCases-Bukkit-2.5.6.jar"}]}]""".toByteArray()
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val request = PluginUpdateRequest.builder().repository("pnFolder", "Cases").apiVersions(1, 2)
                .artifact("(?i)^pnCases-Bukkit-.*\\.jar$", PlatformType.BUKKIT, 17).build()
            val result = ReleaseCatalogueClient(transport, ReleaseCatalogueStore(directory), executor, Duration.ZERO)
                .releases(ReleaseSource("pnFolder", "Cases"), UpdateChannel.STABLE,
                    ProductId.of("cases"), request, PlatformType.BUKKIT)
                .join().single()

            assertEquals("2.5.6", result.version.toString())
            assertEquals(digest, result.artifacts.single().sha256)
        } finally { executor.shutdownNow() }
    }

    @Test fun `does not coalesce fallback catalogues for different platforms`() {
        val releases = URI.create("https://api.github.com/repos/pnFolder/Cases/releases?per_page=30")
        val transport = object : TrustedHttpClient(Duration.ZERO, Duration.ZERO, emptySet()) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray = when (uri) {
                releases -> """[{"tag_name":"v2.5.6","draft":false,"prerelease":false,"assets":[
                    {"name":"pnCases-Bukkit-2.5.6.jar","size":123,"digest":"sha256:${"a".repeat(64)}","browser_download_url":"https://github.com/pnFolder/Cases/b.jar"},
                    {"name":"pnCases-Velocity-2.5.6.jar","size":123,"digest":"sha256:${"b".repeat(64)}","browser_download_url":"https://github.com/pnFolder/Cases/v.jar"}]}]""".toByteArray()
                else -> error("Unexpected URI: $uri")
            }
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val client = ReleaseCatalogueClient(transport, ReleaseCatalogueStore(directory), executor, Duration.ZERO)
            val bukkit = PluginUpdateRequest.builder().repository("pnFolder", "Cases")
                .artifact("(?i)^pnCases-Bukkit-.*\\.jar$", PlatformType.BUKKIT, 17).build()
            val velocity = PluginUpdateRequest.builder().repository("pnFolder", "Cases")
                .artifact("(?i)^pnCases-Velocity-.*\\.jar$", PlatformType.VELOCITY, 17).build()

            val first = client.releases(ReleaseSource("pnFolder", "Cases"), UpdateChannel.STABLE,
                ProductId.of("cases"), bukkit, PlatformType.BUKKIT)
            val second = client.releases(ReleaseSource("pnFolder", "Cases"), UpdateChannel.STABLE,
                ProductId.of("cases"), velocity, PlatformType.VELOCITY)
            assertEquals("pnCases-Bukkit-2.5.6.jar", first.join().single().artifacts.single().file)
            assertEquals("pnCases-Velocity-2.5.6.jar", second.join().single().artifacts.single().file)
        } finally { executor.shutdownNow() }
    }

    private class FixtureTransport : TrustedHttpClient(Duration.ZERO, Duration.ZERO, emptySet()) {
        val releasesUri = URI.create("https://api.github.com/repos/pnFolder/Economy/releases?per_page=30")
        val manifestUri = URI.create("https://github.com/pnFolder/Economy/releases/download/v3.4.0/pn-update.json")
        val calls = ConcurrentHashMap<URI, Int>()
        override fun get(uri: URI, maximumBytes: Int): ByteArray {
            calls.merge(uri, 1, Int::plus)
            return when (uri) {
                releasesUri -> """[{"tag_name":"v3.4.0","draft":false,"prerelease":false,"assets":[{"name":"pn-update.json","browser_download_url":"$manifestUri"}]}]""".toByteArray()
                manifestUri -> """{"schema":1,"component":"economy","version":"3.4.0","channel":"stable","pnLibraryApi":{"minimum":1,"maximum":2},"dependencies":[],"artifacts":[]}""".toByteArray()
                else -> error("Unexpected URI $uri")
            }
        }
    }

    private class FailingTransport : TrustedHttpClient(Duration.ZERO, Duration.ZERO, emptySet()) {
        override fun get(uri: URI, maximumBytes: Int): ByteArray = error("network must not be used: $uri")
    }
}
