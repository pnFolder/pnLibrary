package org.pnfolder.pnlibrary.core.upload

import java.net.URI
import java.time.Instant

/**
 * Receipt returned by a successful upload operation.
 *
 * @property link                 The public link for viewing the report.
 * @property id                   Backend-specific report identifier.
 * @property deleteToken          Token for later deletion (may be blank if backend doesn't support it).
 * @property createdEpochSeconds  Upload timestamp in epoch seconds (0 if unavailable).
 * @property expiresEpochSeconds  Expiry timestamp in epoch seconds (0 if unavailable).
 * @property backend              Backend identifier, e.g. `"mclogs"`.
 */
data class UploadReceipt(
    val link: URI,
    val id: String,
    val deleteToken: String,
    val createdEpochSeconds: Long,
    val expiresEpochSeconds: Long,
    val backend: String,
) {
    /** Whether the provider gave a token that deletes the upload. */
    fun canDelete(): Boolean = deleteToken.isNotBlank()

    override fun toString(): String = link.toString()

    companion object {
        /** A receipt for a backend that gives no delete token and no expiry; the upload counts as made now. */
        fun permanent(link: URI, id: String, backend: String): UploadReceipt = UploadReceipt(
            link = link,
            id = id,
            deleteToken = "",
            createdEpochSeconds = Instant.now().epochSecond,
            expiresEpochSeconds = 0,
            backend = backend,
        )
    }
}
