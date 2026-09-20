package ru.privatenull.pnlibrary.localization.internal

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.localization.TranslationException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.Files
import java.util.zip.ZipFile

internal data class AssetReference(val hash: String, val size: Int)
internal data class ResolvedLanguage(val bytes: ByteArray, val hash: String)
private data class VersionAssets(val metadata: JsonObject, val objects: Map<String, AssetReference>)

internal class MojangAssetResolver(
    private val http: LocalizationHttpClient,
    private val store: VerifiedFileStore,
) {
    private val versionAssets = ConcurrentHashMap<MinecraftVersion, VersionAssets>()
    fun manifestBytes(): ByteArray = http.get(MANIFEST, MANIFEST_LIMIT)

    fun versions(manifest: ByteArray): List<MinecraftVersion> {
        val root = parse(manifest, "version manifest")
        return root.getAsJsonArray("versions").orEmpty()
            .mapNotNull { entry ->
                val value = entry.asJsonObject
                if (value.string("type") != "release") null else MinecraftVersion.parse(value.string("id"))
            }
            .filter { it.known }
            .distinct()
            .sortedWith(compareByDescending<MinecraftVersion> { it.major }.thenByDescending { it.minor }.thenByDescending { it.patch })
    }

    fun locales(version: MinecraftVersion, manifest: ByteArray): List<String> =
        (versionAssets(version, manifest).objects.keys.asSequence()
            .filter { it.startsWith("minecraft/lang/") && (it.endsWith(".json") || it.endsWith(".lang")) }
            .map { it.substringAfterLast('/').substringBeforeLast('.') } + sequenceOf("en_us"))
            .distinct().sorted().toList()

    fun language(version: MinecraftVersion, locale: String, manifest: ByteArray): ResolvedLanguage {
        val assets = versionAssets(version, manifest)
        val reference = assets.objects["minecraft/lang/$locale.json"]
            ?: assets.objects["minecraft/lang/$locale.lang"]
            ?: return languageFromClient(version, locale, assets.metadata)
        val uri = URI.create("https://resources.download.minecraft.net/${reference.hash.take(2)}/${reference.hash}")
        val bytes = http.get(uri, LANGUAGE_LIMIT)
        if (bytes.size != reference.size || store.sha1(bytes) != reference.hash) throw TranslationException(
            TranslationException.Reason.INTEGRITY, "Checksum or size mismatch for Minecraft $version locale $locale",
        )
        return ResolvedLanguage(bytes, reference.hash)
    }

    private fun versionAssets(version: MinecraftVersion, manifest: ByteArray): VersionAssets =
        versionAssets.computeIfAbsent(version) { resolveVersionAssets(it, manifest) }

    private fun resolveVersionAssets(version: MinecraftVersion, manifest: ByteArray): VersionAssets {
        val versionEntry = parse(manifest, "version manifest").getAsJsonArray("versions").orEmpty()
            .map { it.asJsonObject }
            .firstOrNull { it.string("id") == version.text }
            ?: throw TranslationException(
                TranslationException.Reason.UNSUPPORTED_VERSION, "Minecraft ${version.text} is absent from the Mojang manifest",
            )
        val versionUrl = versionEntry.string("url") ?: throw malformed("Version manifest entry has no URL")
        val versionJson = cachedJson(
            store.versionMetadata(version.text), URI.create(versionUrl), VERSION_LIMIT, "version metadata",
            expectedHash = versionEntry.string("sha1"), validator = ::validateVersionMetadata,
        )
        val indexDescriptor = versionJson.getAsJsonObject("assetIndex")
        val indexUrl = indexDescriptor.string("url") ?: throw malformed("Version metadata has no asset index URL")
        val index = cachedJson(
            store.assetIndex(version.text), URI.create(indexUrl), INDEX_LIMIT, "asset index",
            expectedHash = indexDescriptor.string("sha1"),
            expectedSize = indexDescriptor.get("size")?.takeUnless { it.isJsonNull }?.asLong,
            validator = ::validateAssetIndex,
        )
        val objects = index.getAsJsonObject("objects")?.entrySet()?.associate { (key, raw) ->
            val value = raw.asJsonObject
            key to AssetReference(
                hash = value.string("hash") ?: throw malformed("Asset $key has no hash"),
                size = value.get("size")?.asInt ?: throw malformed("Asset $key has no size"),
            )
        } ?: throw malformed("Asset index has no objects")
        return VersionAssets(versionJson, objects)
    }

    private fun cachedJson(
        path: java.nio.file.Path,
        uri: URI,
        limit: Int,
        name: String,
        expectedHash: String? = null,
        expectedSize: Long? = null,
        validator: (JsonObject) -> Unit,
    ): JsonObject {
        fun decode(bytes: ByteArray): JsonObject {
            if ((expectedSize != null && bytes.size.toLong() != expectedSize) ||
                (expectedHash != null && store.sha1(bytes) != expectedHash)
            ) throw TranslationException(
                TranslationException.Reason.INTEGRITY, "Checksum or size mismatch for Minecraft $name",
            )
            val parsed = parse(bytes, name)
            try {
                validator(parsed)
            } catch (error: TranslationException) {
                throw error
            } catch (error: RuntimeException) {
                throw TranslationException(TranslationException.Reason.MALFORMED_DATA, "Malformed Minecraft $name", error)
            }
            return parsed
        }
        store.read(path)?.let { bytes ->
            try {
                return decode(bytes)
            } catch (_: TranslationException) {
                store.quarantine(path)
            }
        }
        val bytes = http.get(uri, limit)
        val parsed = decode(bytes)
        store.write(path, bytes)
        return parsed
    }

    private fun validateVersionMetadata(metadata: JsonObject) {
        val index = metadata.getAsJsonObject("assetIndex") ?: throw malformed("Version metadata has no asset index")
        if (index.string("url").isNullOrBlank()) throw malformed("Version metadata has no asset index URL")
    }

    private fun validateAssetIndex(index: JsonObject) {
        val objects = index.getAsJsonObject("objects") ?: throw malformed("Asset index has no objects")
        objects.entrySet().forEach { (key, raw) ->
            if (!raw.isJsonObject) throw malformed("Asset $key is not an object")
            val value = raw.asJsonObject
            if (value.string("hash").isNullOrBlank()) throw malformed("Asset $key has no hash")
            if (!value.has("size") || value.get("size").isJsonNull || value.get("size").asLong < 0) {
                throw malformed("Asset $key has no valid size")
            }
        }
    }

    private fun languageFromClient(version: MinecraftVersion, locale: String, metadata: JsonObject): ResolvedLanguage {
        val client = metadata.getAsJsonObject("downloads")?.getAsJsonObject("client")
            ?: throw unavailable(version, locale)
        val url = client.string("url") ?: throw unavailable(version, locale)
        val hash = client.string("sha1") ?: throw malformed("Client artifact has no SHA-1")
        val size = client.get("size")?.asInt ?: throw malformed("Client artifact has no size")
        val temporary = try {
            store.temporary(".jar")
        } catch (error: Exception) {
            throw TranslationException(TranslationException.Reason.OFFLINE, "Unable to create temporary Minecraft archive", error)
        }
        try {
            val downloaded = http.download(URI.create(url), CLIENT_LIMIT, temporary)
            if (downloaded != size.toLong() || store.sha1(temporary) != hash) throw TranslationException(
                TranslationException.Reason.INTEGRITY, "Checksum or size mismatch for Minecraft $version client artifact",
            )
            val names = listOf("assets/minecraft/lang/$locale.json", "assets/minecraft/lang/$locale.lang")
            ZipFile(temporary.toFile()).use { zip ->
                val entry = names.asSequence().mapNotNull(zip::getEntry).firstOrNull()
                    ?: throw unavailable(version, locale)
                if (entry.isDirectory || entry.size > LANGUAGE_LIMIT) throw TranslationException(
                    TranslationException.Reason.INTEGRITY, "Minecraft language entry exceeds $LANGUAGE_LIMIT bytes",
                )
                zip.getInputStream(entry).use { input ->
                    val output = ByteArrayOutputStream(minOf(entry.size.coerceAtLeast(0L).toInt(), 8192))
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (output.size() + read > LANGUAGE_LIMIT) throw TranslationException(
                            TranslationException.Reason.INTEGRITY, "Minecraft language entry exceeds $LANGUAGE_LIMIT bytes",
                        )
                        output.write(buffer, 0, read)
                    }
                    val bytes = output.toByteArray()
                    return ResolvedLanguage(bytes, store.sha1(bytes))
                }
            }
        } catch (error: TranslationException) {
            throw error
        } catch (error: Exception) {
            throw TranslationException(
                TranslationException.Reason.MALFORMED_DATA,
                "Unable to read Minecraft $version client archive",
                error,
            )
        } finally {
            try {
                Files.deleteIfExists(temporary)
            } catch (_: Exception) {
                temporary.toFile().deleteOnExit()
            }
        }
    }

    private fun unavailable(version: MinecraftVersion, locale: String) = TranslationException(
        TranslationException.Reason.UNAVAILABLE_LOCALE,
        "Minecraft ${version.text} does not publish locale $locale",
    )

    private fun parse(bytes: ByteArray, name: String): JsonObject = try {
        JsonParser.parseString(String(bytes, StandardCharsets.UTF_8)).asJsonObject
    } catch (error: Exception) {
        throw TranslationException(TranslationException.Reason.MALFORMED_DATA, "Malformed Minecraft $name", error)
    }

    private fun malformed(message: String) = TranslationException(TranslationException.Reason.MALFORMED_DATA, message)
    private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
    private fun com.google.gson.JsonArray?.orEmpty() = this?.toList().orEmpty()

    companion object {
        val MANIFEST: URI = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        private const val MANIFEST_LIMIT = 4 * 1024 * 1024
        private const val VERSION_LIMIT = 4 * 1024 * 1024
        private const val INDEX_LIMIT = 32 * 1024 * 1024
        private const val LANGUAGE_LIMIT = 16 * 1024 * 1024
        private const val CLIENT_LIMIT = 512 * 1024 * 1024
    }
}
