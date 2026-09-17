package ru.privatenull.pnlibrary.update

import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.updates.ComponentId
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.jar.JarFile

data class ArtifactSpecification(
    val component: ComponentId,
    val version: SemanticVersion,
    val supportedApi: ApiVersionRange,
    val fileName: String,
    val size: Long,
    val sha256: String,
) {
    init {
        require(fileName.isNotBlank() && Paths.get(fileName).fileName.toString() == fileName) { "fileName must be a plain file name" }
        require(size > 0) { "artifact size must be positive" }
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "SHA-256 must contain 64 hexadecimal characters" }
    }
}

class ArtifactVerificationException(message: String) : IllegalArgumentException(message)

class ArtifactVerifier(private val maximumBytes: Long) {
    init { require(maximumBytes > 0) { "maximumBytes must be positive" } }

    fun verify(path: Path, expected: ArtifactSpecification) {
        if (!Files.isRegularFile(path)) fail("artifact is not a regular file")
        if (path.fileName.toString() != expected.fileName) fail("artifact file name does not match metadata")
        val size = Files.size(path)
        if (size > maximumBytes) fail("artifact exceeds the configured size limit")
        if (size != expected.size) fail("artifact size does not match metadata")
        if (!sha256(path).equals(expected.sha256, ignoreCase = true)) fail("artifact SHA-256 does not match metadata")

        val metadata = try {
            JarFile(path.toFile()).use { jar ->
                val entry = jar.getJarEntry("META-INF/pnlibrary/plugin.json")
                    ?: fail("artifact does not contain pnLibrary metadata")
                jar.getInputStream(entry).reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
            }
        } catch (error: ArtifactVerificationException) {
            throw error
        } catch (error: Exception) {
            throw ArtifactVerificationException("artifact metadata cannot be read: ${error.message}")
        }
        if (metadata["schemaVersion"]?.asInt != 1) fail("unsupported artifact metadata schema")
        if (metadata["id"]?.asString != expected.component.value) fail("artifact component identity does not match")
        if (metadata["version"]?.asString != expected.version.toString()) fail("artifact version does not match")
        if (metadata["artifact"]?.asString != expected.fileName) fail("embedded artifact name does not match")
        val api = metadata.getAsJsonObject("api") ?: fail("artifact API range is missing")
        if (api["min"]?.asInt != expected.supportedApi.minimum || api["max"]?.asInt != expected.supportedApi.maximum) {
            fail("artifact API range does not match")
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fail(message: String): Nothing = throw ArtifactVerificationException(message)
}
