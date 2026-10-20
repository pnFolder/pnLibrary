package org.pnfolder.pnlibrary.core.journal

import org.pnfolder.pnlibrary.api.logging.LogLevel

/** Maps a library log level to its journal file set. SUCCESS is routine information. */
internal fun LogLevel.toJournalLevel(): JournalLevel = when (this) {
    LogLevel.WARNING -> JournalLevel.WARNING
    LogLevel.ERROR -> JournalLevel.ERROR
    else -> JournalLevel.INFO
}

/** Severity of a journal record. Each level is stored in its own file set, so retention can differ per level. */
internal enum class JournalLevel(val fileGroup: String) {
    INFO("info"),
    WARNING("warning"),
    ERROR("error"),
}

/**
 * One journal entry.
 *
 * @property timeMillis wall-clock time of the event in epoch milliseconds
 * @property level severity, which also selects the file set and retention
 * @property source plugin or component that produced the record, for example `pnlibrary` or `shop`
 * @property type kind of record: `LOG`, `ERROR`, `NOTE`, or `COMPLAINT`
 * @property message short human-readable text, truncated to [MAX_MESSAGE_CHARS] when stored
 * @property details extra key-value facts such as a player name or a plugin version
 */
internal data class JournalRecord(
    val timeMillis: Long,
    val level: JournalLevel,
    val source: String,
    val type: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
) {
    companion object {
        /** Upper bound for stored text; longer messages are cut so one record always fits a block. */
        const val MAX_MESSAGE_CHARS = 8_000

        /** Upper bound for every detail key and value. */
        const val MAX_DETAIL_CHARS = 1_000
    }
}
