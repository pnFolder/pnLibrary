package ru.privatenull.pnlibrary.update

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.nio.charset.StandardCharsets

class ManifestException(
    val field: String,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException("Invalid component manifest field '$field': $message", cause)

class ComponentDescriptorCodec {
    fun decodeRelease(bytes: ByteArray): ComponentRelease = guarded("root") {
        val root = parse(bytes)
        requireInt(root, "schema").also { if (it != SCHEMA) fail("schema", "unsupported schema $it") }
        val api = requireObject(root, "pnLibraryApi")
        val dependencies = requireArray(root, "dependencies").mapIndexed { index, raw -> guarded("dependencies[$index]") {
            val value = raw.asJsonObject
            ComponentDependency(
                ComponentId.of(requireString(value, "component")),
                SemanticVersion.parse(requireString(value, "minimumVersion")),
            )
        } }
        if (dependencies.map { it.component }.distinct().size != dependencies.size) fail("dependencies", "duplicate component")
        val artifacts = requireArray(root, "artifacts").mapIndexed { index, raw -> guarded("artifacts[$index]") {
            val value = raw.asJsonObject
            val java = requireObject(value, "java")
            ArtifactDescriptor(
                requireString(value, "file"),
                platform(requireString(value, "platform")),
                requireInt(java, "minimum"),
                optionalInt(java, "maximum"),
                requireLong(value, "size"),
                requireString(value, "sha256"),
                null,
            )
        } }
        ComponentRelease(
            ComponentId.of(requireString(root, "component")),
            SemanticVersion.parse(requireString(root, "version")),
            channel(requireString(root, "channel")),
            ApiVersionRange(requireInt(api, "minimum"), requireInt(api, "maximum")),
            providesApi = optionalInt(root, "providesApi"),
            dependencies = dependencies,
            repository = optionalString(root, "repository"),
            artifacts = artifacts,
        )
    }

    fun encodeInstalled(descriptor: ComponentDescriptor): ByteArray {
        val root = JsonObject()
        root.addProperty("schema", SCHEMA)
        root.addProperty("component", descriptor.id.value)
        root.addProperty("version", descriptor.version.toString())
        root.add("pnLibraryApi", JsonObject().apply {
            addProperty("minimum", descriptor.supportedApi.minimum)
            addProperty("maximum", descriptor.supportedApi.maximum)
        })
        root.add("managedDependencies", JsonArray().apply {
            descriptor.managedDependencies.forEach { dependency -> add(JsonObject().apply {
                addProperty("component", dependency.component.value)
                addProperty("minimumVersion", dependency.minimumVersion.toString())
                addProperty("repositoryOwner", dependency.repositoryOwner)
                addProperty("repositoryName", dependency.repositoryName)
            }) }
        })
        return root.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun decodeInstalled(bytes: ByteArray): ComponentDescriptor = guarded("root") {
        val root = parse(bytes)
        requireInt(root, "schema").also { if (it != SCHEMA) fail("schema", "unsupported schema $it") }
        val api = requireObject(root, "pnLibraryApi")
        val builder = ComponentDescriptor.builder(requireString(root, "component"), requireString(root, "version"))
            .pnLibraryApi(requireInt(api, "minimum"), requireInt(api, "maximum"))
        requireArray(root, "managedDependencies").forEachIndexed { index, raw -> guarded("managedDependencies[$index]") {
            val value = raw.asJsonObject
            builder.managedDependency(
                requireString(value, "component"), requireString(value, "minimumVersion"),
                requireString(value, "repositoryOwner"), requireString(value, "repositoryName"),
            )
        } }
        builder.build()
    }

    private fun parse(bytes: ByteArray): JsonObject {
        if (bytes.size > MAX_MANIFEST_BYTES) fail("root", "manifest exceeds $MAX_MANIFEST_BYTES bytes")
        return try {
            val element = JsonParser.parseString(String(bytes, StandardCharsets.UTF_8))
            if (!element.isJsonObject) fail("root", "expected object")
            element.asJsonObject
        } catch (error: ManifestException) {
            throw error
        } catch (error: Exception) {
            throw ManifestException("root", "malformed JSON", error)
        }
    }

    private fun requireString(value: JsonObject, name: String): String =
        value.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }
            ?: fail(name, "expected non-blank string")
    private fun requireInt(value: JsonObject, name: String): Int =
        try { value.get(name)?.asInt ?: fail(name, "expected integer") } catch (error: Exception) { fail(name, "expected integer", error) }
    private fun optionalInt(value: JsonObject, name: String): Int? =
        value.get(name)?.takeUnless { it.isJsonNull }?.let { try { it.asInt } catch (error: Exception) { fail(name, "expected integer", error) } }
    private fun optionalString(value: JsonObject, name: String): String? =
        value.get(name)?.takeUnless { it.isJsonNull }?.let {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString || it.asString.isBlank()) fail(name, "expected non-blank string")
            it.asString
        }
    private fun requireLong(value: JsonObject, name: String): Long =
        try { value.get(name)?.asLong ?: fail(name, "expected integer") } catch (error: Exception) { fail(name, "expected integer", error) }
    private fun requireObject(value: JsonObject, name: String): JsonObject =
        value.get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: fail(name, "expected object")
    private fun requireArray(value: JsonObject, name: String): JsonArray =
        value.get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: fail(name, "expected array")
    private fun platform(value: String): PlatformType = guarded("platform") { PlatformType.valueOf(value.uppercase()) }
    private fun channel(value: String): UpdateChannel = guarded("channel") { UpdateChannel.valueOf(value.uppercase()) }
    private inline fun <T> guarded(field: String, action: () -> T): T = try { action() }
        catch (error: ManifestException) { throw error }
        catch (error: Exception) { throw ManifestException(field, error.message ?: "invalid value", error) }
    private fun fail(field: String, message: String, cause: Throwable? = null): Nothing = throw ManifestException(field, message, cause)

    companion object {
        const val SCHEMA = 1
        const val MAX_MANIFEST_BYTES = 256 * 1024
    }
}
