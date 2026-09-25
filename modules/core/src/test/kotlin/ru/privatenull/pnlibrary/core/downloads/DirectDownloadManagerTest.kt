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
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.update.TrustedHttpClient
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class DirectDownloadManagerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `installs ordinary file only inside declared data directory`() {
        val payload = "safe-data".toByteArray()
        val request = FileDownloads.builder().dataDirectory(directory.resolve("plugin-data"))
            .file("data") { it.url("https://example.org/data.bin")
                .destination(DownloadDestination.DATA_FOLDER, "models/data.bin") }.build()
        manager(payload).use { it.install(request, request.files.single()) }

        assertArrayEquals(payload, Files.readAllBytes(directory.resolve("plugin-data/models/data.bin")))
    }

    @Test
    fun `manual download works when automatic policy is disabled`() {
        val payload = "manual".toByteArray()
        val request = FileDownloads.builder().dataDirectory(directory.resolve("manual-data"))
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
    fun `automatic external dependency is staged in server update directory`() {
        val payload = pluginJar("Vault", "1.7.3")
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.3")
            .url("https://example.org/download")
            .automaticDownload(true)
            .build()

        manager(payload).use { manager ->
            val registration = requireNotNull(manager.registerDependencies(Any(), listOf(dependency)))
            registration.downloadNow().toCompletableFuture().join()
            assertArrayEquals(payload, Files.readAllBytes(directory.resolve("plugins/update/Vault.jar")))
        }
    }

    @Test
    fun `dependency validation uses the descriptor of the active platform`() {
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.3")
            .url("https://example.org/Vault.jar")
            .automaticDownload(true)
            .build()
        val payload = pluginJar(mapOf(
            "plugin.yml" to "name: Vault\nversion: 1.7.3\nmain: example.Main\n",
            "bungee.yml" to "name: DifferentPlugin\nversion: 1.7.3\nmain: example.Main\n",
        ))

        manager(payload, platformType = PlatformType.BUNGEECORD).use { manager ->
            val registration = requireNotNull(manager.registerDependencies(Any(), listOf(dependency)))
            assertEquals(DownloadState.FAILED, registration.downloadNow().toCompletableFuture().join().single().state)
        }
    }

    @Test
    fun `dependency download rejects a jar whose declared plugin identity does not match`() {
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.3")
            .url("https://example.org/Vault.jar")
            .automaticDownload(true)
            .build()

        manager(pluginJar("NotVault", "1.7.3")).use { manager ->
            val registration = requireNotNull(manager.registerDependencies(Any(), listOf(dependency)))
            val snapshots = registration.downloadNow().toCompletableFuture().join()
            assertEquals(DownloadState.FAILED, snapshots.single().state)
            assertFalse(Files.exists(directory.resolve("plugins/update/Vault.jar")))
        }
    }

    @Test
    fun `dependency at minimum version is not downloaded again`() {
        val dependency = ExternalPluginDependency.builder("Vault", "1.7.3")
            .url("https://example.org/Vault.jar")
            .automaticDownload(true)
            .build()

        manager(pluginJar("Vault", "1.7.3"), installed = mapOf("Vault" to "1.7.3")).use { manager ->
            assertTrue(manager.registerDependencies(Any(), listOf(dependency)) == null)
        }
    }

    @Test
    fun `does not publish first item when a later item fails verification`() {
        val request = FileDownloads.builder().dataDirectory(directory.resolve("plugin-data"))
            .file("data") { it.url("https://example.org/data.bin")
                .destination(DownloadDestination.DATA_FOLDER, "data.bin") }
            .file("broken") { it.url("https://example.org/broken.bin")
                .integrity(1, "0".repeat(64))
                .destination(DownloadDestination.DATA_FOLDER, "broken.bin") }
            .build()

        manager("not-a-jar".toByteArray()).use { manager ->
            assertThrows(IllegalArgumentException::class.java) {
                manager.installBatch(request, request.files)
            }
        }

        assertFalse(Files.exists(directory.resolve("plugin-data/data.bin")))
    }

    @Test
    fun `closing manager before publication does not publish prepared files`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val payload = "late-download".toByteArray()
        val request = FileDownloads.builder().dataDirectory(directory.resolve("plugin-data"))
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

    private fun manager(
        bytes: ByteArray,
        automatic: Boolean = true,
        installed: Map<String, String> = emptyMap(),
        platformType: PlatformType = PlatformType.BUKKIT,
    ): DirectDownloadManager {
        return manager(bytes, automatic, null, null, installed, platformType)
    }

    private fun blockingManager(bytes: ByteArray, entered: CountDownLatch, release: CountDownLatch): DirectDownloadManager =
        manager(bytes, true, entered, release, emptyMap(), PlatformType.BUKKIT)

    private fun manager(
        bytes: ByteArray,
        automatic: Boolean,
        entered: CountDownLatch?,
        release: CountDownLatch?,
        installed: Map<String, String>,
        platformType: PlatformType,
    ): DirectDownloadManager {
        val platform = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PlatformAdapter::class.java)) { _, method, _ ->
            when (method.name) { "getType" -> platformType; "installedPlugins" -> installed; "details" -> emptyMap<String, String>(); else -> null }
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

    private fun pluginJar(name: String, version: String): ByteArray = ByteArrayOutputStream().also { output ->
        pluginJar(mapOf("plugin.yml" to "name: $name\nversion: $version\nmain: example.Main\n"), output)
    }.toByteArray()

    private fun pluginJar(entries: Map<String, String>): ByteArray = ByteArrayOutputStream().also { output ->
        pluginJar(entries, output)
    }.toByteArray()

    private fun pluginJar(entries: Map<String, String>, output: ByteArrayOutputStream) {
        JarOutputStream(output).use { archive ->
            entries.forEach { (name, contents) ->
                archive.putNextEntry(JarEntry(name))
                archive.write(contents.toByteArray())
                archive.closeEntry()
            }
        }
    }
}
