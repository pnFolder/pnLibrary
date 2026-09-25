package ru.privatenull.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import ru.privatenull.pnlibrary.api.downloads.DownloadState
import ru.privatenull.pnlibrary.api.downloads.PluginDownloads
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.update.ProductDescriptorCodec
import ru.privatenull.pnlibrary.update.EmbeddedDescriptorReader
import ru.privatenull.pnlibrary.update.TrustedHttpClient
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DirectDownloadManagerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `installs ordinary file only inside declared data directory`() {
        val payload = "safe-data".toByteArray()
        val request = PluginDownloads.builder().dataDirectory(directory.resolve("plugin-data"))
            .file("data") { it.url("https://example.org/data.bin")
                .destination(DownloadDestination.DATA_FOLDER, "models/data.bin") }.build()
        manager(payload).use { it.install(request, request.declarations.single()) }

        assertArrayEquals(payload, Files.readAllBytes(directory.resolve("plugin-data/models/data.bin")))
    }

    @Test
    fun `manual download works when automatic policy is disabled`() {
        val payload = "manual".toByteArray()
        val request = PluginDownloads.builder().dataDirectory(directory.resolve("manual-data"))
            .file("manual") { it.url("https://example.org/manual.bin")
                .destination(DownloadDestination.DATA_FOLDER, "manual.bin") }.build()
        val manager = manager(payload, automatic = false)
        manager.use {
            val registration = it.register(Any(), request)
            assertFalse(Files.exists(directory.resolve("manual-data/manual.bin")))
            assertEquals(DownloadState.STAGED, registration.downloadNow().toCompletableFuture().join().single().state)
            assertArrayEquals(payload, Files.readAllBytes(directory.resolve("manual-data/manual.bin")))
            registration.close()
            assertTrue(registration.isClosed)
            assertThrows(UnsupportedOperationException::class.java) {
                @Suppress("UNCHECKED_CAST")
                (registration.snapshots() as MutableList<Any>).clear()
            }
        }
    }

    @Test
    fun `verifies embedded identity version and API before staging component`() {
        val descriptor = ProductDescriptor.builder("pneconomy", "2.0.0").pnLibraryApi(1, 2).build()
        val jar = ByteArrayOutputStream().also { output -> JarOutputStream(output).use { archive ->
            archive.putNextEntry(JarEntry(EmbeddedDescriptorReader.ENTRY))
            archive.write(ProductDescriptorCodec().encodeInstalled(descriptor)); archive.closeEntry()
        } }.toByteArray()
        val request = PluginDownloads.builder().component("pneconomy") {
            it.version("2.0.0").apiVersions(1, 2).platform(PlatformType.BUKKIT)
                .url("https://example.org/pnEconomy.jar")
        }.build()

        manager(jar).use { it.install(request, request.declarations.single()) }

        assertTrue(Files.isRegularFile(directory.resolve("plugins/update/pnEconomy.jar")))
    }

    @Test
    fun `does not publish first item when a later item fails verification`() {
        val request = PluginDownloads.builder().dataDirectory(directory.resolve("plugin-data"))
            .file("data") { it.url("https://example.org/data.bin")
                .destination(DownloadDestination.DATA_FOLDER, "data.bin") }
            .component("broken") { it.version("1.0.0").apiVersion(1)
                .url("https://example.org/broken.jar") }
            .build()

        manager("not-a-jar".toByteArray()).use { manager ->
            assertThrows(IllegalArgumentException::class.java) {
                manager.installBatch(request, request.declarations)
            }
        }

        assertFalse(Files.exists(directory.resolve("plugin-data/data.bin")))
    }

    @Test
    fun `closing manager before publication does not publish prepared files`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val payload = "late-download".toByteArray()
        val request = PluginDownloads.builder().dataDirectory(directory.resolve("plugin-data"))
            .file("data") { it.url("https://example.org/data.bin")
                .destination(DownloadDestination.DATA_FOLDER, "data.bin") }.build()
        blockingManager(payload, entered, release).use { manager ->
            val registration = manager.register(Any(), request)
            val future = registration.downloadNow().toCompletableFuture()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            manager.close()
            release.countDown()

            assertTrue(future.isCompletedExceptionally)
            assertFalse(Files.exists(directory.resolve("plugin-data/data.bin")))
        }
    }

    private fun manager(bytes: ByteArray, automatic: Boolean = true): DirectDownloadManager {
        return manager(bytes, automatic, null, null)
    }

    private fun blockingManager(bytes: ByteArray, entered: CountDownLatch, release: CountDownLatch): DirectDownloadManager =
        manager(bytes, true, entered, release)

    private fun manager(
        bytes: ByteArray,
        automatic: Boolean,
        entered: CountDownLatch?,
        release: CountDownLatch?,
    ): DirectDownloadManager {
        val platform = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PlatformAdapter::class.java)) { _, method, _ ->
            when (method.name) { "getType" -> PlatformType.BUKKIT; "installedPlugins", "details" -> emptyMap<String, String>(); else -> null }
        } as PlatformAdapter
        val http = object : TrustedHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(1), setOf("example.org")) {
            override fun get(uri: URI, maximumBytes: Int): ByteArray {
                entered?.countDown()
                release?.await(5, TimeUnit.SECONDS)
                return bytes.also { require(it.size <= maximumBytes) }
            }
        }
        return DirectDownloadManager(platform, directory.resolve("plugins/pnLibrary"), DownloadConfiguration(
            automatic = automatic, allowedHosts = setOf("example.org"),
        ), http)
    }
}
