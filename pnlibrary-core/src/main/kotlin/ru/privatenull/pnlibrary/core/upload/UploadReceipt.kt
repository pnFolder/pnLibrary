package ru.privatenull.pnlibrary.core.upload

import java.net.URI

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
    /** Returns `true` when a deletion token is available. */
    fun canDelete(): Boolean = deleteToken.isNotBlank()

    override fun toString(): String = link.toString()
}
