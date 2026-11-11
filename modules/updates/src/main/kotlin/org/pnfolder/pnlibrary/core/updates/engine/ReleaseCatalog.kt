package org.pnfolder.pnlibrary.core.updates.engine

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.version.ApiVersionRange
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.net.URI
import java.time.Instant

/** Controls whether a catalog may use its validated local cache. */
internal enum class RefreshMode { CACHED, FORCE_REMOTE }

/** Invalid release-catalog field with its logical [field] path. */
internal class ManifestException(
    /** Logical JSON field path that failed validation. */
    val field: String,
    message: String,
    cause: Throwable? = null,
) :
    IllegalArgumentException("Invalid release catalog field '$field': $message", cause)

/**
 * Parsed release catalog.
 *
 * @property product normalized product identifier
 * @property releases ordered published releases
 */
internal data class ReleaseCatalog(val product: String, val releases: List<CatalogRelease>)

/**
 * One catalog release.
 *
 * @property version semantic release version
 * @property channel declared release maturity
 * @property description human-readable summary
 * @property publishedAt UTC publication instant
 * @property api supported pnLibrary API generations
 * @property artifacts platform-specific downloadable artifacts
 */
internal data class CatalogRelease(
    val version: SemanticVersion,
    val channel: UpdateChannel,
    val description: String,
    val changelogUrl: URI? = null,
    val publishedAt: Instant,
    val api: ApiVersionRange,
    val artifacts: List<CatalogArtifact>,
)

/**
 * One platform artifact declared by a catalog release.
 *
 * @property file safe JAR filename
 * @property platform required platform family, or [PlatformType.UNIVERSAL] for one JAR built for every platform
 * @property minecraft optional compatible Minecraft range
 * @property platformApi optional compatible proxy API range
 * @property javaMinimum oldest supported Java feature version
 * @property javaMaximum newest supported Java feature version
 * @property url HTTPS artifact location
 * @property sha256 optional published SHA-256 digest
 * @property size optional published byte size
 */
internal data class CatalogArtifact(
    val file: String,
    val platform: PlatformType,
    val minecraft: VersionRangeText? = null,
    val platformApi: VersionRangeText? = null,
    val javaMinimum: Int,
    val javaMaximum: Int? = null,
    val url: URI,
    val sha256: String? = null,
    val size: Long? = null,
)

/** Strict schema-1 JSON decoder for bounded release catalogs. */
internal class ReleaseCatalogCodec(private val maximumBytes: Int = MAX_BYTES) {
    /** Decodes and validates catalog [bytes]. */
    fun decode(bytes: ByteArray): ReleaseCatalog {
        require(bytes.size <= maximumBytes) { "Release catalog exceeds $maximumBytes bytes" }
        val root = try { JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject }
        catch (error: Exception) { throw ManifestException("root", "malformed JSON", error) }
        val schema = root.requiredInt("schema")
        if (schema != SCHEMA) fail("schema", "unsupported schema $schema")
        val product = root.requiredString("product")
        val releases = root.requiredArray("releases").mapIndexed { index, value ->
            decodeRelease(value.asJsonObject, "releases[$index]")
        }
        require(releases.map { it.version }.distinct().size == releases.size) {
            "Release catalog contains duplicate versions"
        }
        return ReleaseCatalog(product, releases)
    }

    private fun decodeRelease(value: JsonObject, field: String): CatalogRelease = try {
        val artifacts = value.requiredArray("artifacts").mapIndexed { index, raw ->
            decodeArtifact(raw.asJsonObject, "$field.artifacts[$index]")
        }
        require(artifacts.isNotEmpty()) { "$field.artifacts must not be empty" }
        val version = SemanticVersion.parse(value.requiredString("version"))
        val declaredChannel = channel(value.requiredString("channel"))
        CatalogRelease(
            version,
            declaredChannel,
            value.requiredString("description"),
            value.optionalHttpsUrl("changelogUrl"),
            Instant.parse(value.requiredString("publishedAt")),
            value.requiredObject("api").apiRange(),
            artifacts,
        )
    } catch (error: ManifestException) { throw error }
      catch (error: Exception) { throw ManifestException(field, error.message ?: "invalid release", error) }

    private fun decodeArtifact(value: JsonObject, field: String): CatalogArtifact {
        val file = value.requiredString("file")
        require(SAFE_FILE.matches(file) && file.endsWith(".jar", true)) { "$field.file is not a safe JAR filename" }
        val platform = try { PlatformType.valueOf(value.requiredString("platform").uppercase()) }
            catch (error: Exception) { throw ManifestException("$field.platform", "unknown platform", error) }
        // Absent only for a UNIVERSAL artifact that imposes no Java restriction beyond the library's own floor.
        val java = if (value.has("java")) value.requiredObject("java") else null
        val minimum = java?.requiredInt("minimum") ?: 8
        val maximum = java?.optionalInt("maximum")
        require(minimum >= 8 && (maximum == null || maximum >= minimum)) { "$field.java range is invalid" }
        val minecraft = value.optionalRange("minecraft")
        val platformApi = value.optionalRange("platformApi")
        if (platform == PlatformType.UNIVERSAL) {
            require(minecraft == null && platformApi == null) {
                "$field: a UNIVERSAL artifact must not declare minecraft or platformApi"
            }
        } else {
            require(!(minecraft != null && platform.isProxy)) { "$field.minecraft is only valid for server platforms" }
            require(!(platformApi != null && platform.isServer)) { "$field.platformApi is only valid for proxy platforms" }
        }
        val url = try { URI(value.requiredString("url")) }
            catch (error: Exception) { throw ManifestException("$field.url", "invalid URL", error) }
        require(url.scheme.equals("https", true)) { "$field.url must use HTTPS" }
        val sha256 = value.optionalString("sha256")?.also {
            require(it.matches(SHA256)) { "$field.sha256 must have 64 hexadecimal characters" }
        }?.lowercase()
        val size = value.optionalLong("size")?.also { require(it > 0) { "$field.size must be positive" } }
        return CatalogArtifact(file, platform, minecraft, platformApi, minimum, maximum, url, sha256, size)
    }

    private fun JsonObject.apiRange() = ApiVersionRange(requiredInt("minimum"), requiredInt("maximum"))
    private fun JsonObject.optionalRange(name: String): VersionRangeText? {
        if (!has(name)) return null
        val value = get(name)
        if (value.isJsonNull || !value.isJsonObject) fail(name, "expected object or omitted field")
        val obj = value.asJsonObject
        return VersionRangeText(obj.requiredString("minimum"), obj.optionalString("maximum"))
    }
    private fun JsonObject.optionalHttpsUrl(name: String): URI? {
        if (!has(name)) return null
        val value = get(name)
        if (value.isJsonNull || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            fail(name, "expected HTTPS URL or omitted field")
        }
        val uri = try { URI.create(value.asString) }
        catch (error: Exception) { fail(name, "invalid URL", error) }
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) {
            "$name must use HTTPS"
        }
        return uri
    }
    private fun JsonObject.requiredString(name: String) = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString?.takeIf { it.isNotBlank() } ?: fail(name, "expected non-blank string")
    private fun JsonObject.optionalString(name: String): String? = if (!has(name)) null else {
        val value = get(name)
        if (value.isJsonNull) fail(name, "null is not allowed; omit the field")
        value.asString.takeIf { it.isNotBlank() } ?: fail(name, "expected non-blank string")
    }
    private fun JsonObject.requiredInt(name: String) = try { get(name)?.asInt ?: fail(name, "expected integer") }
        catch (error: ManifestException) { throw error }
        catch (error: Exception) { fail(name, "expected integer", error) }
    private fun JsonObject.optionalInt(name: String): Int? = if (!has(name)) null else {
        val value = get(name)
        if (value.isJsonNull) fail(name, "null is not allowed; omit the field")
        try { value.asInt } catch (error: Exception) { fail(name, "expected integer", error) }
    }
    private fun JsonObject.optionalLong(name: String): Long? = if (!has(name)) null else {
        val value = get(name)
        if (value.isJsonNull) fail(name, "null is not allowed; omit the field")
        try { value.asLong } catch (error: Exception) { fail(name, "expected integer", error) }
    }
    private fun JsonObject.requiredObject(name: String) = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
        ?: fail(name, "expected object")
    private fun JsonObject.requiredArray(name: String): JsonArray = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?: fail(name, "expected array")
    private fun channel(value: String) = try { UpdateChannel.valueOf(value.uppercase()) }
        catch (error: Exception) { throw ManifestException("channel", "unknown channel", error) }
    private fun fail(field: String, message: String, cause: Throwable? = null): Nothing = throw ManifestException(field, message, cause)

    /** Public schema and size limits used by clients. */
    companion object {
        /** Supported catalog schema generation. */
        const val SCHEMA = 1
        /** Default maximum encoded catalog size. */
        const val MAX_BYTES = 1024 * 1024
        private val SAFE_FILE = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
        private val SHA256 = Regex("(?i)[0-9a-f]{64}")
    }
}

/** Textual compatibility interval for Minecraft or a native platform API, as written in a release catalog. */
internal data class VersionRangeText(val minimum: String, val maximum: String? = null) {
    init {
        require(minimum.isNotBlank()) { "Minimum version must not be blank" }
        require(maximum == null || maximum.isNotBlank()) { "Maximum version must not be blank" }
    }
}
