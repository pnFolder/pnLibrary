package ru.privatenull.pnlibrary.localization.internal

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.localization.TranslationException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

internal data class AssetReference(val hash: String, val size: Int)
internal data class ResolvedLanguage(val bytes: ByteArray, val hash: String)
private data class VersionAssets(val metadata: JsonObject, val objects: Map<String, AssetReference>)

internal class MojangAssetResolver(
    private val http: LocalizationHttpClient,
    private val store: VerifiedFileStore,
) {
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

    private fun versionAssets(version: MinecraftVersion, manifest: ByteArray): VersionAssets {
        val versionUrl = parse(manifest, "version manifest").getAsJsonArray("versions").orEmpty()
            .map { it.asJsonObject }
            .firstOrNull { it.string("id") == version.text }
            ?.string("url") ?: throw TranslationException(
                TranslationException.Reason.UNSUPPORTED_VERSION, "Minecraft ${version.text} is absent from the Mojang manifest",
            )
        val versionJson = parse(http.get(URI.create(versionUrl), VERSION_LIMIT), "version metadata")
        val indexUrl = versionJson.getAsJsonObject("assetIndex")?.string("url")
            ?: throw malformed("Version metadata has no asset index")
        val index = parse(http.get(URI.create(indexUrl), INDEX_LIMIT), "asset index")
        val objects = index.getAsJsonObject("objects")?.entrySet()?.associate { (key, raw) ->
            val value = raw.asJsonObject
            key to AssetReference(
                hash = value.string("hash") ?: throw malformed("Asset $key has no hash"),
                size = value.get("size")?.asInt ?: throw malformed("Asset $key has no size"),
            )
        } ?: throw malformed("Asset index has no objects")
        return VersionAssets(versionJson, objects)
    }

    private fun languageFromClient(version: MinecraftVersion, locale: String, metadata: JsonObject): ResolvedLanguage {
        val client = metadata.getAsJsonObject("downloads")?.getAsJsonObject("client")
            ?: throw unavailable(version, locale)
        val url = client.string("url") ?: throw unavailable(version, locale)
        val hash = client.string("sha1") ?: throw malformed("Client artifact has no SHA-1")
        val size = client.get("size")?.asInt ?: throw malformed("Client artifact has no size")
        val jar = http.get(URI.create(url), CLIENT_LIMIT)
        if (jar.size != size || store.sha1(jar) != hash) throw TranslationException(
            TranslationException.Reason.INTEGRITY, "Checksum or size mismatch for Minecraft $version client artifact",
        )
        val names = setOf("assets/minecraft/lang/$locale.json", "assets/minecraft/lang/$locale.lang")
        ZipInputStream(ByteArrayInputStream(jar)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name in names) {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = zip.read(buffer)
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
        }
        throw unavailable(version, locale)
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
