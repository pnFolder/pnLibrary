package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.net.URI
import java.nio.file.Paths

/** Optional textual compatibility interval for Minecraft or a native platform API. */
data class VersionRangeText(val minimum: String, val maximum: String? = null) {
    init {
        require(minimum.isNotBlank()) { "minimum version must not be blank" }
        require(maximum == null || maximum.isNotBlank()) { "maximum version must not be blank" }
    }
}

/** Runtime constraints attached to one exact product artifact. */
data class ArtifactCompatibility(
    val platform: PlatformType,
    val minecraft: VersionRangeText? = null,
    val platformApi: VersionRangeText? = null,
    val pnLibraryApi: ApiVersionRange,
    val minimumJava: Int,
    val maximumJava: Int? = null,
) {
    init {
        require(minimumJava >= 8) { "minimum Java must be at least 8" }
        require(maximumJava == null || maximumJava >= minimumJava) { "maximum Java must be >= minimum Java" }
    }
}

/** Exact downloadable JAR and all facts needed to verify and select it. */
data class ProductArtifact(
    val file: String,
    val compatibility: ArtifactCompatibility,
    val size: Long,
    val sha256: String,
    val downloadUri: URI? = null,
) {
    init {
        require(file.endsWith(".jar", true) && Paths.get(file).fileName.toString() == file) {
            "artifact file must be a plain JAR filename"
        }
        require(size > 0) { "artifact size must be positive" }
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "artifact SHA-256 is invalid" }
        require(downloadUri == null || downloadUri.scheme.equals("https", true)) {
            "artifact download URI must use HTTPS"
        }
    }
}

/** Schema-1 release asset generated for one product version. */
data class ProductReleaseManifest(
    val productId: ProductId,
    val displayName: String,
    val version: SemanticVersion,
    val channel: UpdateChannel,
    val artifacts: List<ProductArtifact>,
) {
    init {
        require(displayName.isNotBlank()) { "display name must not be blank" }
        require(artifacts.isNotEmpty()) { "release manifest must contain at least one artifact" }
        require(artifacts.map { it.file.lowercase() }.distinct().size == artifacts.size) {
            "release manifest contains duplicate artifact filenames"
        }
    }
}
