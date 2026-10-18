package ru.privatenull.pnlibrary.api.updates

import java.net.URI

/**
 * Exact external artifact allowed only under server-side trust policy.
 *
 * @property uri verified HTTPS artifact location
 * @property size expected byte length, when known
 * @property sha256 expected SHA-256 digest, when known
 */
class ExternalArtifact(
    val uri: URI,
    val size: Long?,
    val sha256: String?,
) {
    init {
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) { "External artifact must use HTTPS" }
        require(size == null || size > 0) { "External artifact size must be positive" }
        require(sha256 == null || SHA_256.matches(sha256)) { "External artifact SHA-256 is invalid" }
    }

    /** Integrity validation constants. */
    companion object {
        private val SHA_256 = Regex("[0-9a-fA-F]{64}")
    }
}
