package ru.privatenull.pnlibrary.api.updates

import ru.privatenull.pnlibrary.api.platform.PlatformType
import java.net.URI

/**
 * One exact platform/Java release artifact.
 *
 * @property file safe JAR filename
 * @property platform required server/proxy platform
 * @property minimumJava oldest supported Java feature version
 * @property maximumJava newest supported Java feature version, or `null` when unbounded
 * @property size optional publisher-provided byte length
 * @property sha256 optional publisher-provided SHA-256 digest
 * @property downloadUri optional HTTPS artifact location
 */
class ArtifactDescriptor(
    val file: String,
    val platform: PlatformType,
    val minimumJava: Int,
    val maximumJava: Int?,
    /** Optional publisher metadata. The updater computes these values after download. */
    val size: Long? = null,
    val sha256: String? = null,
    val downloadUri: URI?,
) {
    init {
        require(SAFE_FILE.matches(file) && file.endsWith(".jar", true)) { "Unsafe artifact filename: $file" }
        require(minimumJava >= 8) { "Minimum Java must be at least 8" }
        require(maximumJava == null || maximumJava >= minimumJava) { "Maximum Java must be >= minimum Java" }
        require(size == null || size > 0) { "Artifact size must be positive" }
        require(sha256 == null || SHA_256.matches(sha256)) { "Artifact SHA-256 is invalid" }
        require(downloadUri == null || downloadUri.scheme.equals("https", true)) { "Artifact URL must use HTTPS" }
    }

    /** Returns whether [javaFeature] is inside this artifact's Java range. */
    fun supports(javaFeature: Int): Boolean = javaFeature >= minimumJava &&
        (maximumJava == null || javaFeature <= maximumJava)

    /** Filename and integrity validation constants. */
    companion object {
        private val SAFE_FILE = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
        private val SHA_256 = Regex("[0-9a-fA-F]{64}")
    }
}
