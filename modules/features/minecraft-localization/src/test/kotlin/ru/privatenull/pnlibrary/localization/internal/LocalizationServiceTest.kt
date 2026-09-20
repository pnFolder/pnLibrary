package ru.privatenull.pnlibrary.localization.internal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.localization.TranslationRequest
import ru.privatenull.pnlibrary.localization.TranslationSource
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletionException

class LocalizationServiceTest {
    @TempDir lateinit var directory: Path

    @Test fun `loads only requested locale and reuses memory`() {
        val fixture = Fixture()
        service(fixture).use { service ->
            val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
            val first = service.load(request).toCompletableFuture().join().locale("ru_ru")
            val second = service.load(request).toCompletableFuture().join().locale("ru_ru")
            assertEquals("Алмазный меч", first.translate("item.minecraft.diamond_sword").orElseThrow())
            assertEquals(TranslationSource.MEMORY, second.metadata.source)
            assertEquals(0, fixture.calls.keys.count { "en_us" in it.toString() })
            assertEquals(1, fixture.assetDownloads)
        }
    }

    @Test fun `shares concurrent same locale download`() {
        val fixture = Fixture(delayMillis = 75)
        service(fixture).use { service ->
            val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
            val first = service.load(request).toCompletableFuture()
            val second = service.load(request).toCompletableFuture()
            first.join(); second.join()
            assertEquals(1, fixture.assetDownloads)
        }
    }

    @Test fun `completed in flight entry does not suppress explicit refresh`() {
        val fixture = Fixture()
        service(fixture).use { service ->
            val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
            service.load(request).toCompletableFuture().join()
            service.refresh(request).toCompletableFuture().join()
            assertEquals(2, fixture.assetDownloads)
        }
    }

    @Test fun `discovers supported release versions and locales`() {
        val fixture = Fixture()
        service(fixture).use { service ->
            assertEquals(listOf(MinecraftVersion.V1_21_4), service.availableVersions().toCompletableFuture().join())
            assertEquals(listOf("en_us", "ru_ru"), service.availableLocales(MinecraftVersion.V1_21_4).toCompletableFuture().join())
        }
    }

    @Test fun `reopens a downloaded locale from disk without network`() {
        val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
        service(Fixture()).use { it.load(request).toCompletableFuture().join() }
        service(FailingClient()).use { service ->
            val locale = service.load(request).toCompletableFuture().join().locale("ru_ru")
            assertEquals(TranslationSource.DISK, locale.metadata.source)
            assertEquals("Алмазный меч", locale.translate("item.minecraft.diamond_sword").orElseThrow())
        }
    }

    @Test fun `quarantines corrupt disk locale instead of serving it`() {
        val path = directory.resolve("minecraft/translations/1.21.4/ru_ru.json")
        Files.createDirectories(path.parent)
        Files.write(path, "not-json".toByteArray())
        val request = TranslationRequest.builder().version(MinecraftVersion.V1_21_4).locale("ru_ru").build()
        service(FailingClient()).use { service ->
            assertThrows(CompletionException::class.java) { service.load(request).toCompletableFuture().join() }
        }
        assertTrue(Files.list(path.parent).use { files -> files.anyMatch { ".corrupt-" in it.fileName.toString() } })
    }

    @Test fun `closed service rejects new operations`() {
        val service = service(Fixture())
        service.close()
        assertThrows(ru.privatenull.pnlibrary.localization.TranslationException::class.java) { service.availableVersions() }
    }

    private fun service(fixture: LocalizationHttpClient) = DefaultMinecraftLocalization(
        directory, Duration.ofHours(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
        4, 2, null, fixture,
    )

    private class Fixture(private val delayMillis: Long = 0) : LocalizationHttpClient(Duration.ZERO, Duration.ZERO) {
        val calls = ConcurrentHashMap<URI, Int>()
        @Volatile var assetDownloads = 0
        private val language = "{\"item.minecraft.diamond_sword\":\"Алмазный меч\"}".toByteArray(StandardCharsets.UTF_8)
        private val hash = MessageDigest.getInstance("SHA-1").digest(language).joinToString("") { "%02x".format(it) }
        private val versionUri = URI.create("https://piston-meta.mojang.com/v1/1.21.4.json")
        private val indexUri = URI.create("https://piston-meta.mojang.com/v1/1.21.4-assets.json")
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
        override fun get(uri: URI, maximumBytes: Int): ByteArray = throw java.io.IOException("offline")
    }
}
