package org.pnfolder.pnlibrary.core.logging

import org.pnfolder.pnlibrary.api.logging.LogLevel

/**
 * Marks lines the native logger cannot tell apart: it has no success level, so a success would look like plain info.
 * Warnings and errors already carry the platform's own level and colour.
 */
object ConsoleStyle {
    const val SUCCESS_MARK = "[OK] "

    fun decorate(level: LogLevel, message: String): String =
        if (level == LogLevel.SUCCESS) SUCCESS_MARK + message else message
}
