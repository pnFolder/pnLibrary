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
    /** Short id shared by repeats of the same event: same level, source, first message line and exception. */
    val fingerprint: String
        get() {
            val key = listOf(level.name, source, message.lineSequence().first().take(200), details["exception"].orEmpty())
                .joinToString("\u0000")
            return "%08x".format(key.hashCode())
        }

    companion object {
        /** Upper bound for stored text; longer messages are cut so one record always fits a block. */
        const val MAX_MESSAGE_CHARS = 8_000

        /** Upper bound for every detail key and value. */
        const val MAX_DETAIL_CHARS = 1_000

        /** Detail key that holds the full stack trace of an error, including its causes. */
        const val STACKTRACE = "stacktrace"

        /** Upper bound for the stack trace; at three bytes per character it still fits one UTF string. */
        const val MAX_STACK_CHARS = 20_000

        /** Returns the whole stack trace of [error] with every `Caused by` section. */
        fun stackTraceOf(error: Throwable): String =
            java.io.StringWriter().also { error.printStackTrace(java.io.PrintWriter(it)) }.toString().trim()
    }
}
