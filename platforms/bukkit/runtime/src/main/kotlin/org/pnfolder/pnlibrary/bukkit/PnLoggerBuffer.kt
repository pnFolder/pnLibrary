package org.pnfolder.pnlibrary.bukkit

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.logging.PnLogger
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Routes log calls to PnLogger when the library has started. Before that, outputs immediately to
 * the native console logger and buffers the entry. On [flush], buffered entries are replayed via
 * [PnLogger.record] at their original level, going to the journal only — no second console line.
 */
internal class PnLoggerBuffer(private val native: Logger, private val source: () -> PnLogger?) {
    private data class Entry(val level: LogLevel, val message: String, val error: Throwable?)
    private val entries = mutableListOf<Entry>()

    @JvmOverloads
    fun log(level: LogLevel, message: String, error: Throwable? = null) {
        source()?.log(level, message, error) ?: run {
            val javaLevel = when (level) {
                LogLevel.WARNING -> Level.WARNING
                LogLevel.ERROR, LogLevel.CRITICAL -> Level.SEVERE
                else -> Level.INFO
            }
            if (error != null) native.log(javaLevel, message, error) else native.log(javaLevel, message)
            entries.add(Entry(level, message, error))
        }
    }

    fun flush() {
        val logger = source() ?: return
        entries.forEach { (level, msg, err) -> logger.record(level, msg, err) }
        entries.clear()
    }
}
