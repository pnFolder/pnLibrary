package ru.privatenull.pnlibrary.update

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.VersionRangeText
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.net.URI
import java.time.Instant

enum class RefreshMode { CACHED, FORCE_REMOTE }

class ManifestException(val field: String, message: String, cause: Throwable? = null) :
    IllegalArgumentException("Invalid release catalog field '$field': $message", cause)

data class ReleaseCatalog(val product: String, val releases: List<CatalogRelease>)

data class CatalogRelease(
    val version: SemanticVersion,
    val channel: UpdateChannel,
    val description: String,
    val publishedAt: Instant,
    val api: ApiVersionRange,
    val artifacts: List<CatalogArtifact>,
)

data class CatalogArtifact(
    val file: String,
    val platform: PlatformType,
    val minecraft: VersionRangeText? = null,
    val platformApi: VersionRangeText? = null,
    val javaMinimum: Int,
    val javaMaximum: Int? = null,
    val url: URI,
)

class ReleaseCatalogCodec(private val maximumBytes: Int = MAX_BYTES) {
    fun decode(bytes: ByteArray): ReleaseCatalog {
        require(bytes.size <= maximumBytes) { "release catalog exceeds $maximumBytes bytes" }
        val root = try { JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject }
        catch (error: Exception) { throw ManifestException("root", "malformed JSON", error) }
        val schema = root.requiredInt("schema")
        if (schema != SCHEMA) fail("schema", "unsupported schema $schema")
        val product = root.requiredString("product")
        val releases = root.requiredArray("releases").mapIndexed { index, value ->
            decodeRelease(value.asJsonObject, "releases[$index]")
        }
        require(releases.map { it.version }.distinct().size == releases.size) {
            "release catalog contains duplicate versions"
        }
        return ReleaseCatalog(product, releases)
    }

    private fun decodeRelease(value: JsonObject, field: String): CatalogRelease = try {
        val artifacts = value.requiredArray("artifacts").mapIndexed { index, raw ->
            decodeArtifact(raw.asJsonObject, "$field.artifacts[$index]")
        }
        require(artifacts.isNotEmpty()) { "$field.artifacts must not be empty" }
        CatalogRelease(
            SemanticVersion.parse(value.requiredString("version")),
            channel(value.requiredString("channel")),
            value.requiredString("description"),
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
        val java = value.requiredObject("java")
        val minimum = java.requiredInt("minimum")
        val maximum = java.optionalInt("maximum")
        require(minimum >= 8 && (maximum == null || maximum >= minimum)) { "$field.java range is invalid" }
        val minecraft = value.optionalRange("minecraft")
        val platformApi = value.optionalRange("platformApi")
        require(!(minecraft != null && platform.isProxy)) { "$field.minecraft is only valid for server platforms" }
        require(!(platformApi != null && platform.isServer)) { "$field.platformApi is only valid for proxy platforms" }
        val url = try { URI(value.requiredString("url")) }
            catch (error: Exception) { throw ManifestException("$field.url", "invalid URL", error) }
        require(url.scheme.equals("https", true)) { "$field.url must use HTTPS" }
        return CatalogArtifact(file, platform, minecraft, platformApi, minimum, maximum, url)
    }

    private fun JsonObject.apiRange() = ApiVersionRange(requiredInt("minimum"), requiredInt("maximum"))
    private fun JsonObject.optionalRange(name: String): VersionRangeText? {
        if (!has(name)) return null
        val value = get(name)
        if (value.isJsonNull || !value.isJsonObject) fail(name, "expected object or omitted field")
        val obj = value.asJsonObject
        return VersionRangeText(obj.requiredString("minimum"), obj.optionalString("maximum"))
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
    private fun JsonObject.requiredObject(name: String) = get(name)?.takeIf { it.isJsonObject }?.asJsonObject
        ?: fail(name, "expected object")
    private fun JsonObject.requiredArray(name: String): JsonArray = get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?: fail(name, "expected array")
    private fun channel(value: String) = try { UpdateChannel.valueOf(value.uppercase()) }
        catch (error: Exception) { throw ManifestException("channel", "unknown channel", error) }
    private fun fail(field: String, message: String, cause: Throwable? = null): Nothing = throw ManifestException(field, message, cause)

    companion object {
        const val SCHEMA = 1
        const val MAX_BYTES = 1024 * 1024
        private val SAFE_FILE = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
    }
}
