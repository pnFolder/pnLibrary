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
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class ReleaseCatalogueClientTest {
    @TempDir lateinit var directory: Path

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

    @Test fun `forced refresh bypasses fresh disk cache`() {
        val releases = URI.create("https://api.github.com/repos/pnFolder/Cases/releases?per_page=30")
        var calls = 0
        val transport = object : TrustedHttpClient(Duration.ZERO, Duration.ZERO, emptySet()) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray {
                assertEquals(releases, uri)
                calls++
                val version = if (calls == 1) "2.4.0" else "2.5.0"
                return """[{"tag_name":"v$version","draft":false,"prerelease":false,"assets":[{
                    "name":"pnCases-$version-bukkit-java8.jar","size":123,"digest":"sha256:${"a".repeat(64)}",
                    "browser_download_url":"https://github.com/pnFolder/Cases/$version.jar"}]}]""".toByteArray()
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val client = ReleaseCatalogueClient(transport, ReleaseCatalogueStore(directory), executor, Duration.ofHours(1))
            val request = PluginUpdateRequest.builder().repository("pnFolder", "Cases")
                .artifact("(?i)^pnCases-.*-bukkit-java8\\.jar$", PlatformType.BUKKIT, 8).build()
            fun load(refresh: RefreshMode) = client.releases(
                ReleaseSource("pnFolder", "Cases"), UpdateChannel.STABLE, ProductId.of("cases"), request,
                emptyList(), PlatformType.BUKKIT, refresh,
            ).join().single().version.toString()

            assertEquals("2.4.0", load(RefreshMode.CACHED))
            assertEquals("2.4.0", load(RefreshMode.CACHED))
            assertEquals("2.5.0", load(RefreshMode.FORCE_REMOTE))
            assertEquals(2, calls)
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

    @Test fun `uses embedded candidate metadata for API and Java compatibility`() {
        val releasesUri = URI.create("https://api.github.com/repos/pnFolder/Cases/releases?per_page=30")
        val assetUri = URI.create("https://github.com/pnFolder/Cases/cases.jar")
        val runtimeJava = Runtime.version().feature()
        val jar = componentJar("cases", "2.5.0", 2, 3, 17, runtimeJava)
        val digest = MessageDigest.getInstance("SHA-256").digest(jar).joinToString("") { "%02x".format(it) }
        val transport = object : TrustedHttpClient(Duration.ZERO, Duration.ZERO, emptySet()) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray = when (uri) {
                releasesUri -> """[{"tag_name":"v2.5.0","draft":false,"prerelease":false,"assets":[{
                    "name":"pnCases-2.5.0-bukkit-java17.jar","size":${jar.size},"digest":"sha256:$digest",
                    "browser_download_url":"$assetUri"}]}]""".toByteArray()
                assetUri -> jar
                else -> error("Unexpected URI: $uri")
            }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val request = PluginUpdateRequest.builder().repository("pnFolder", "Cases").apiVersion(1)
                .artifact("(?i)^pnCases-.*-bukkit-java17\\.jar$", PlatformType.BUKKIT, 8).build()
            val release = ReleaseCatalogueClient(
                transport, ReleaseCatalogueStore(directory), executor, Duration.ZERO, inspectArtifacts = true,
            ).releases(
                ReleaseSource("pnFolder", "Cases"), UpdateChannel.STABLE, ProductId.of("cases"), request,
                emptyList(), PlatformType.BUKKIT,
            ).join().single()

            assertEquals(2, release.supportedApi.minimum)
            assertEquals(3, release.supportedApi.maximum)
            assertEquals(17, release.artifacts.single().minimumJava)
            assertEquals(runtimeJava, release.artifacts.single().maximumJava)
        } finally { executor.shutdownNow() }
    }

    @Test fun `distinguishes empty release pattern mismatch and incompatible Java`() {
        fun failure(assets: String, minimumJava: Int = 8): String {
            val transport = object : TrustedHttpClient(Duration.ZERO, Duration.ZERO, emptySet()) {
                override fun get(uri: URI, maximumBytes: Int): ByteArray =
                    """[{"tag_name":"v2.5.0","draft":false,"prerelease":false,"assets":[$assets]}]""".toByteArray()
            }
            val executor = Executors.newSingleThreadExecutor()
            return try {
                val request = PluginUpdateRequest.builder().repository("pnFolder", "Cases")
                    .artifact("^expected\\.jar$", PlatformType.BUKKIT, minimumJava).build()
                val error = assertThrows(Exception::class.java) {
                    ReleaseCatalogueClient(transport, ReleaseCatalogueStore(directory), executor, Duration.ZERO)
                        .releases(ReleaseSource("pnFolder", "Cases"), UpdateChannel.STABLE,
                            ProductId.of("cases"), request, PlatformType.BUKKIT).join()
                }
                generateSequence(error as Throwable?) { it.cause }.last().message.orEmpty()
            } finally { executor.shutdownNow() }
        }

        assertTrue(failure("").contains("не содержит JAR"))
        assertTrue(failure("""{"name":"other.jar","size":1,"digest":"sha256:${"a".repeat(64)}","browser_download_url":"https://example.org/other.jar"}""")
            .contains("artifact-pattern"))
        assertTrue(failure("""{"name":"expected.jar","size":1,"digest":"sha256:${"a".repeat(64)}","browser_download_url":"https://example.org/expected.jar"}""", 999)
            .contains("Java"))
    }

    private fun componentJar(
        id: String,
        version: String,
        minimumApi: Int,
        maximumApi: Int,
        minimumJava: Int,
        maximumJava: Int?,
    ): ByteArray = ByteArrayOutputStream().also { bytes ->
        JarOutputStream(bytes).use { jar ->
            jar.putNextEntry(JarEntry(EmbeddedDescriptorReader.ENTRY))
            jar.write(ProductDescriptorCodec().encodeInstalled(
                ru.privatenull.pnlibrary.api.updates.ProductDescriptor.builder(id, version)
                    .pnLibraryApi(minimumApi, maximumApi).java(minimumJava, maximumJava).build(),
            ))
            jar.closeEntry()
        }
    }.toByteArray()

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
