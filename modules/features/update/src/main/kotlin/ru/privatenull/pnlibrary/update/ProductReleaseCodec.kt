package ru.privatenull.pnlibrary.update

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.net.URI

/** Strict schema-1 codec for the generated `pn-release.json` asset. */
class ProductReleaseCodec(private val maximumBytes: Int = 256 * 1024) {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun decode(bytes: ByteArray): ProductReleaseManifest {
        require(bytes.size <= maximumBytes) { "release manifest exceeds $maximumBytes bytes" }
        val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        require(root.get("schema")?.asInt == 1) { "unsupported release manifest schema" }
        require(!root.has("dependencies")) { "dependencies belong to module code, not release manifests" }
        val artifacts = root.requireArray("artifacts").map { raw ->
            val value = raw.asJsonObject
            val java = value.requireObject("java")
            ProductArtifact(
                value.requireString("file"),
                ArtifactCompatibility(
                    PlatformType.valueOf(value.requireString("platform").uppercase()),
                    value.optionalRange("minecraft"),
                    value.optionalRange("platformApi"),
                    value.requireObject("pnLibraryApi").apiRange(),
                    java.requireInt("minimum"),
                    java.get("maximum")?.asInt,
                ),
                value.requireLong("size"),
                value.requireString("sha256"),
                value.get("downloadUrl")?.asString?.let(URI::create),
            )
        }
        return ProductReleaseManifest(
            ProductId.of(root.requireString("productId")),
            root.requireString("displayName"),
            SemanticVersion.parse(root.requireString("version")),
            UpdateChannel.valueOf(root.requireString("channel").uppercase()),
            artifacts,
        )
    }

    fun encode(manifest: ProductReleaseManifest): ByteArray {
        val root = JsonObject().apply {
            addProperty("schema", 1)
            addProperty("productId", manifest.productId.value)
            addProperty("displayName", manifest.displayName)
            addProperty("version", manifest.version.toString())
            addProperty("channel", manifest.channel.name.lowercase())
            add("artifacts", com.google.gson.JsonArray().also { array ->
                manifest.artifacts.sortedBy { it.file.lowercase() }.forEach { artifact ->
                    array.add(JsonObject().apply {
                        addProperty("file", artifact.file)
                        addProperty("platform", artifact.compatibility.platform.name)
                        artifact.compatibility.minecraft?.let { add("minecraft", it.json()) }
                        artifact.compatibility.platformApi?.let { add("platformApi", it.json()) }
                        add("pnLibraryApi", artifact.compatibility.pnLibraryApi.json())
                        add("java", JsonObject().apply {
                            addProperty("minimum", artifact.compatibility.minimumJava)
                            artifact.compatibility.maximumJava?.let { addProperty("maximum", it) }
                        })
                        addProperty("size", artifact.size)
                        addProperty("sha256", artifact.sha256.lowercase())
                        artifact.downloadUri?.let { addProperty("downloadUrl", it.toString()) }
                    })
                }
            })
        }
        return (gson.toJson(root) + "\n").toByteArray(Charsets.UTF_8)
    }

    private fun VersionRangeText.json() = JsonObject().apply {
        addProperty("minimum", minimum)
        maximum?.let { addProperty("maximum", it) }
    }

    private fun ApiVersionRange.json() = JsonObject().apply {
        addProperty("minimum", minimum)
        addProperty("maximum", maximum)
    }

    private fun JsonObject.optionalRange(name: String): VersionRangeText? =
        get(name)?.asJsonObject?.let { VersionRangeText(it.requireString("minimum"), it.get("maximum")?.asString) }

    private fun JsonObject.apiRange() = ApiVersionRange(requireInt("minimum"), requireInt("maximum"))
    private fun JsonObject.requireString(name: String): String =
        get(name)?.asString?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("$name is required")
    private fun JsonObject.requireInt(name: String): Int =
        get(name)?.asInt ?: throw IllegalArgumentException("$name is required")
    private fun JsonObject.requireLong(name: String): Long =
        get(name)?.asLong ?: throw IllegalArgumentException("$name is required")
    private fun JsonObject.requireObject(name: String): JsonObject =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: throw IllegalArgumentException("$name object is required")
    private fun JsonObject.requireArray(name: String) =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: throw IllegalArgumentException("$name array is required")
}
