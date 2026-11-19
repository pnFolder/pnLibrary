package org.pnfolder.pnlibrary.api.dependencies.external

import org.pnfolder.pnlibrary.api.internal.isHttps
import org.pnfolder.pnlibrary.api.updates.requirePositiveSize
import org.pnfolder.pnlibrary.api.updates.requireSha256
import java.net.URI

/**
 * Exact external artifact allowed only under server-side trust policy.
 *
 * @property uri verified HTTPS artifact location
 * @property size expected byte length, when known
 * @property sha256 expected SHA-256 digest, when known
 * @property mirrors other HTTPS addresses of the same file; without a checksum the copies must agree with each other
 */
class ExternalArtifact(
    val uri: URI,
    val size: Long?,
    val sha256: String?,
    val mirrors: List<URI> = emptyList(),
) {
    init {
        require(uri.isHttps()) { "External artifact must use HTTPS" }
        requirePositiveSize(size, "External artifact")
        requireSha256(sha256, "External artifact")
        require(mirrors.all { it.isHttps() }) { "External artifact mirrors must use HTTPS" }
    }
}
