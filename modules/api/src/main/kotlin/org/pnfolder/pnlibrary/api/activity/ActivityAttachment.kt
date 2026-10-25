package org.pnfolder.pnlibrary.api.activity

/**
 * Metadata describing a file or byte payload attached to an activity event.
 *
 * @property id unique attachment identifier
 * @property eventId identifier of the owning event
 * @property name caller-visible attachment name
 * @property contentType media type recorded for the payload
 * @property size payload size in bytes
 * @property sha256 lowercase SHA-256 digest used to verify payload integrity
 */
data class ActivityAttachment(
    val id: String,
    val eventId: String,
    val name: String,
    val contentType: String,
    val size: Long,
    val sha256: String,
)
