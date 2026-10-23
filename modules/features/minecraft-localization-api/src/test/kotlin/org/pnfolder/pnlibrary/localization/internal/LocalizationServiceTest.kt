package org.pnfolder.pnlibrary.localization.internal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.localization.TranslationException
import org.pnfolder.pnlibrary.localization.TranslationRequest
import org.pnfolder.pnlibrary.localization.TranslationSource
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalizationServiceTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `loads only requested locale and reuses memory`() {
        val fixture = Fixture()
        service(fixture).use { service ->
            val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
            val first = service.load(request).toCompletableFuture().join().locale("ru_ru")
            val second = service.load(request).toCompletableFuture().join().locale("ru_ru")
            assertEquals("Алмазный меч", first.translate("item.minecraft.diamond_sword").orElseThrow())
            assertEquals(TranslationSource.MEMORY, second.metadata.source)
            assertSame(first.keys(), second.keys())
            assertEquals(0, fixture.calls.keys.count { "en_us" in it.toString() })
            assertEquals(1, fixture.assetDownloads)
        }
    }

    @Test
    fun `reuses version metadata and asset index across operations`() {
        val fixture = Fixture()
        service(fixture).use { service ->
            val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
            assertEquals(listOf("en_us", "ru_ru"), service.availableLocales(MinecraftVersion.V1_21_4).toCompletableFuture().join())
            service.load(request).toCompletableFuture().join()
            service.refresh(request).toCompletableFuture().join()
            assertEquals(1, fixture.calls[fixture.versionUri])
            assertEquals(1, fixture.calls[fixture.indexUri])
        }
    }

    @Test
    fun `shares concurrent same locale download`() {
        val fixture = Fixture(delayMillis = 75)
        service(fixture).use { service ->
            val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
            val first = service.load(request).toCompletableFuture()
            val second = service.load(request).toCompletableFuture()
            first.join(); second.join()
            assertEquals(1, fixture.assetDownloads)
        }
    }

    @Test
    fun `completed in flight entry does not suppress explicit refresh`() {
        val fixture = Fixture()
        service(fixture).use { service ->
            val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
            service.load(request).toCompletableFuture().join()
            service.refresh(request).toCompletableFuture().join()
            assertEquals(2, fixture.assetDownloads)
        }
    }

    @Test
    fun `discovers supported release versions and locales`() {
        val fixture = Fixture()
        service(fixture).use { service ->
            assertEquals(listOf(MinecraftVersion.V1_21_4), service.availableVersions().toCompletableFuture().join())
            assertEquals(listOf("en_us", "ru_ru"), service.availableLocales(MinecraftVersion.V1_21_4).toCompletableFuture().join())
        }
    }

    @Test
    fun `reopens a downloaded locale from disk without network`() {
        val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
        service(Fixture()).use { it.load(request).toCompletableFuture().join() }
        service(FailingClient()).use { service ->
            val locale = service.load(request).toCompletableFuture().join().locale("ru_ru")
            assertEquals(TranslationSource.DISK, locale.metadata.source)
            assertEquals("Алмазный меч", locale.translate("item.minecraft.diamond_sword").orElseThrow())
        }
    }

    @Test
    fun `reopens version asset catalog from disk without network`() {
        service(Fixture()).use { service ->
            assertEquals(listOf("en_us", "ru_ru"), service.availableLocales(MinecraftVersion.V1_21_4).toCompletableFuture().join())
        }
        service(FailingClient()).use { service ->
            assertEquals(listOf("en_us", "ru_ru"), service.availableLocales(MinecraftVersion.V1_21_4).toCompletableFuture().join())
        }
    }

    @Test
    fun `quarantines structurally corrupt version catalog and downloads a clean copy`() {
        val path = directory.resolve("minecraft/versions/1.21.4.json")
        Files.createDirectories(path.parent)
        Files.write(path, "{}".toByteArray())
        service(Fixture()).use { service ->
            assertEquals(listOf("en_us", "ru_ru"), service.availableLocales(MinecraftVersion.V1_21_4).toCompletableFuture().join())
        }
        assertTrue(Files.list(path.parent).use { files -> files.anyMatch { ".corrupt-" in it.fileName.toString() } })
    }

    @Test
    fun `extracts embedded locale through streamed client archive and removes temporary file`() {
        val fixture = ClientArchiveFixture()
        service(fixture).use { service ->
            val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("en_us").build()
            val locale = service.load(request).toCompletableFuture().join().locale("en_us")
            assertEquals("Diamond Sword", locale.translate("item.minecraft.diamond_sword").orElseThrow())
        }
        assertTrue(fixture.streamed)
        val temporary = directory.resolve("minecraft/temporary")
        assertTrue(!Files.exists(temporary) || Files.list(temporary).use { files -> !files.findAny().isPresent })
    }

    @Test
    fun `quarantines corrupt disk locale instead of serving it`() {
        val path = directory.resolve("minecraft/translations/1.21.4/ru_ru.json")
        Files.createDirectories(path.parent)
        Files.write(path, "not-json".toByteArray())
        val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
        service(FailingClient()).use { service ->
            assertThrows(CompletionException::class.java) { service.load(request).toCompletableFuture().join() }
        }
        assertTrue(Files.list(path.parent).use { files -> files.anyMatch { ".corrupt-" in it.fileName.toString() } })
    }

    @Test
    fun `closed service rejects new operations`() {
        val service = service(Fixture())
        service.close()
        assertThrows(TranslationException::class.java) { service.availableVersions() }
    }

    @Test
    fun `rejected locale submissions fail asynchronously and allow load and refresh retries`() {
        val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
        for (refresh in listOf(false, true)) {
            val executor = RejectOnceExecutor()
            try {
                service(Fixture(), executor).use { service ->
                    val rejected = (if (refresh) service.refresh(request) else service.load(request)).toCompletableFuture()
                    assertTrue(rejected.isCompletedExceptionally)
                    assertSame(executor.rejection, assertThrows(CompletionException::class.java) { rejected.join() }.cause)

                    val retry = (if (refresh) service.refresh(request) else service.load(request)).toCompletableFuture()
                    assertEquals("Алмазный меч", retry.get(5, TimeUnit.SECONDS).locale("ru_ru")
                        .translate("item.minecraft.diamond_sword").orElseThrow())
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `callers sharing a rejected locale submission receive the same failure`() {
        val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
        lateinit var service: DefaultMinecraftLocalization
        var shared: CompletableFuture<*>? = null
        val executor = RejectOnceExecutor { shared = service.load(request).toCompletableFuture() }
        try {
            service = service(Fixture(), executor)
            service.use {
                val rejected = it.load(request).toCompletableFuture()
                val second = requireNotNull(shared)
                assertTrue(rejected.isCompletedExceptionally)
                assertTrue(second.isCompletedExceptionally)
                assertSame(executor.rejection, assertThrows(CompletionException::class.java) { rejected.join() }.cause)
                assertSame(executor.rejection, assertThrows(CompletionException::class.java) { second.join() }.cause)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `rejected metadata submissions fail their returned futures`() {
        val executor = Executors.newSingleThreadExecutor()
        executor.shutdown()
        service(Fixture(), executor).use { service ->
            val operations = listOf(service.availableVersions(), service.availableLocales(MinecraftVersion.V1_21_4))
            for (operation in operations) {
                val future = operation.toCompletableFuture()
                assertTrue(future.isCompletedExceptionally)
                assertInstanceOf(RejectedExecutionException::class.java,
                    assertThrows(CompletionException::class.java) { future.join() }.cause)
            }
        }
    }

    private fun service(
        fixture: LocalizationHttpClient,
        executor: ExecutorService? = null,
        layouts: KeyboardLayouts = KeyboardLayouts.create(),
    ) = DefaultMinecraftLocalization(
        directory, Duration.ofHours(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
        4, 2, executor, fixture, layouts,
    )

    @Test
    fun `loaded tables search with the layouts of the service that loaded them`() {
        val layouts = KeyboardLayouts.create()
        service(Fixture(), layouts = layouts).use { service ->
            val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
            val table = service.load(request).toCompletableFuture().join().locale("ru_ru")

            assertSame(layouts, service.layouts)
            assertSame(layouts, table.layouts)
            assertSame(layouts, table.keys().layouts)
        }
        layouts.register(KeyboardLayout.of("service-test", "abcdefghijklmnopqrstuvwxyz012345678")).close()
    }

    @Test
    fun `each service has its own layouts unless one is supplied to the builder`() {
        val shared = KeyboardLayouts.create()
        val first = org.pnfolder.pnlibrary.localization.MinecraftLocalization.builder().cacheDirectory(directory).build()
        val second = org.pnfolder.pnlibrary.localization.MinecraftLocalization.builder().cacheDirectory(directory).layouts(shared).build()
        first.use { second.use { assertSame(shared, second.layouts); assertNotSame(first.layouts, second.layouts) } }
    }

    @Test
    fun `close settles queued metadata and locale operations without shutting down supplied executor`() {
        val executor = Executors.newSingleThreadExecutor()
        val occupied = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.execute { occupied.countDown(); release.await() }
        assertTrue(occupied.await(5, TimeUnit.SECONDS))
        try {
            val service = service(Fixture(), executor)
            val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
            val operations = listOf(service.availableVersions(), service.availableLocales(MinecraftVersion.V1_21_4), service.load(request))
            service.close()
            for (operation in operations) {
                val error = assertThrows(CompletionException::class.java) { operation.toCompletableFuture().join() }.cause
                assertEquals(TranslationException.Reason.CLOSED, (error as TranslationException).reason)
            }
            assertFalse(executor.isShutdown)
            release.countDown()
            assertEquals(42, executor.submit<Int> { 42 }.get(5, TimeUnit.SECONDS))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test
    fun `close racing executor submission settles the accepted operation`() {
        lateinit var service: DefaultMinecraftLocalization
        val executor = object : ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) {
            override fun execute(command: Runnable) {
                service.close()
                super.execute(command)
            }
        }
        try {
            service = service(Fixture(), executor)
            val future = service.availableVersions().toCompletableFuture()
            val error = assertThrows(CompletionException::class.java) { future.join() }.cause
            assertEquals(TranslationException.Reason.CLOSED, (error as TranslationException).reason)
        } finally { executor.shutdownNow() }
    }

    private class RejectOnceExecutor(private val beforeRejection: () -> Unit = {}) :
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue()) {
        val rejection = RejectedExecutionException("Test executor rejected the submission")
        private val rejectNext = AtomicBoolean(true)

        override fun execute(command: Runnable) {
            if (rejectNext.compareAndSet(true, false)) {
                beforeRejection()
                throw rejection
            }
            super.execute(command)
        }
    }

    private class Fixture(private val delayMillis: Long = 0) : LocalizationHttpClient(Duration.ZERO, Duration.ZERO) {
        val calls = ConcurrentHashMap<URI, Int>()
        @Volatile
        var assetDownloads = 0
        private val language = "{\"item.minecraft.diamond_sword\":\"Алмазный меч\"}".toByteArray(StandardCharsets.UTF_8)
        private val hash = MessageDigest.getInstance("SHA-1").digest(language).joinToString("") { "%02x".format(it) }
        val versionUri = URI.create("https://piston-meta.mojang.com/v1/1.21.4.json")
        val indexUri = URI.create("https://piston-meta.mojang.com/v1/1.21.4-assets.json")
        private val assetUri = URI.create("https://resources.download.minecraft.net/${hash.take(2)}/$hash")

        override fun get(uri: URI, maximumBytes: Int): ByteArray {
            calls.merge(uri, 1, Int::plus)
            return when (uri) {
                MojangAssetResolver.MANIFEST -> """{"versions":[
                    {"id":"1.21.4","type":"release","url":"$versionUri"},
                    {"id":"99.1","type":"release","url":"https://piston-meta.mojang.com/v1/future.json"},
                    {"id":"1.21.5-pre1","type":"snapshot","url":"https://piston-meta.mojang.com/v1/snapshot.json"}
                ]}""".toByteArray()
                versionUri -> """{"assetIndex":{"url":"$indexUri"}}""".toByteArray()
                indexUri -> """{"objects":{"minecraft/lang/ru_ru.json":{"hash":"$hash","size":${language.size}}}}""".toByteArray()
                assetUri -> {
                    assetDownloads++
                    if (delayMillis > 0) Thread.sleep(delayMillis)
                    language
                }
                else -> error("Unexpected URI $uri")
            }
        }
    }

    private class FailingClient : LocalizationHttpClient(Duration.ZERO, Duration.ZERO) {
        override fun get(uri: URI, maximumBytes: Int): ByteArray = throw IOException("offline")
    }

    private class ClientArchiveFixture : LocalizationHttpClient(Duration.ZERO, Duration.ZERO) {
        private val versionUri = URI.create("https://piston-meta.mojang.com/v1/client-version.json")
        private val indexUri = URI.create("https://piston-meta.mojang.com/v1/client-assets.json")
        private val clientUri = URI.create("https://piston-data.mojang.com/v1/client.jar")
        private val language = "{\"item.minecraft.diamond_sword\":\"Diamond Sword\"}".toByteArray()
        private val archive = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("assets/minecraft/lang/en_us.json"))
                zip.write(language)
                zip.closeEntry()
            }
        }.toByteArray()
        private val archiveHash = MessageDigest
            .getInstance("SHA-1")
            .digest(archive)
            .joinToString("") { "%02x".format(it) }
        @Volatile
        var streamed = false

        override fun get(uri: URI, maximumBytes: Int): ByteArray = when (uri) {
            MojangAssetResolver.MANIFEST -> """{"versions":[{"id":"1.21.4","type":"release","url":"$versionUri"}]}""".toByteArray()
            versionUri -> """{
                "assetIndex":{"url":"$indexUri"},
                "downloads":{"client":{"url":"$clientUri","sha1":"$archiveHash","size":${archive.size}}}
            }""".trimIndent().toByteArray()
            indexUri -> """{"objects":{}}""".toByteArray()
            clientUri -> error("Client archive must not be materialized through get()")
            else -> error("Unexpected URI $uri")
        }

        override fun download(uri: URI, maximumBytes: Int, destination: Path): Long {
            check(uri == clientUri)
            check(archive.size <= maximumBytes)
            streamed = true
            Files.write(destination, archive)
            return archive.size.toLong()
        }
    }
}
