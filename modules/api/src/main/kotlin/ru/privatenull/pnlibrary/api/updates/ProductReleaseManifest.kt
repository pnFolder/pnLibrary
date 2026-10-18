package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.internal.isHttps
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.net.URI
import java.nio.file.Paths
import java.util.Locale

/**
 * Optional textual compatibility interval for Minecraft or a native platform API.
 *
 * @property minimum oldest compatible version
 * @property maximum newest compatible version, or `null` when unbounded
 */
data class VersionRangeText(val minimum: String, val maximum: String? = null) {
    init {
        require(minimum.isNotBlank()) { "Minimum version must not be blank" }
        require(maximum == null || maximum.isNotBlank()) { "Maximum version must not be blank" }
    }
}

/**
 * Runtime constraints attached to one exact product artifact.
 *
 * @property platform required server/proxy platform
 * @property minecraft optional compatible Minecraft version range
 * @property platformApi optional compatible native platform API range
 * @property pnLibraryApi compatible pnLibrary API generations
 * @property minimumJava oldest supported Java feature version
 * @property maximumJava newest supported Java feature version, or `null` when unbounded
 */
data class ArtifactCompatibility(
    val platform: PlatformType,
    val minecraft: VersionRangeText? = null,
    val platformApi: VersionRangeText? = null,
    val pnLibraryApi: ApiVersionRange,
    val minimumJava: Int,
    val maximumJava: Int? = null,
) {
    init {
        require(minimumJava >= 8) { "Minimum Java must be at least 8" }
        require(maximumJava == null || maximumJava >= minimumJava) { "Maximum Java must be >= minimum Java" }
    }
}

/**
 * Exact downloadable JAR and all facts needed to verify and select it.
 *
 * @property file plain JAR filename
 * @property compatibility runtime constraints used for selection
 * @property size expected byte length
 * @property sha256 expected SHA-256 digest
 * @property downloadUri optional explicit HTTPS location
 */
data class ProductArtifact(
    val file: String,
    val compatibility: ArtifactCompatibility,
    val size: Long,
    val sha256: String,
    val downloadUri: URI? = null,
) {
    init {
        require(file.endsWith(".jar", true) && Paths.get(file).fileName.toString() == file) {
            "Artifact file must be a plain JAR filename"
        }
        require(size > 0) { "Artifact size must be positive" }
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Artifact SHA-256 is invalid" }
        require(downloadUri == null || downloadUri.isHttps()) {
            "Artifact download URI must use HTTPS"
        }
    }
}

/**
 * Schema-1 release asset generated for one product version.
 *
 * @property productId normalized product identity
 * @property displayName human-readable product name
 * @property version released semantic version
 * @property channel declared release maturity
 * @property artifacts platform-specific downloadable JARs
 */
data class ProductReleaseManifest(
    val productId: ProductId,
    val displayName: String,
    val version: SemanticVersion,
    val channel: UpdateChannel,
    val artifacts: List<ProductArtifact>,
) {
    init {
        require(displayName.isNotBlank()) { "Release display name must not be blank" }
        require(artifacts.isNotEmpty()) { "Release manifest must contain at least one artifact" }
        require(artifacts.map { it.file.lowercase(Locale.ROOT) }.distinct().size == artifacts.size) {
            "Release manifest contains duplicate artifact filenames"
        }
    }
}
