package org.pnfolder.pnlibrary.spi.platform

import org.pnfolder.pnlibrary.api.logging.LogLevel

/** Optional native log stream observed by the shared diagnostics runtime. */
interface PlatformLogObservation {
    /** Starts forwarding native log lines to [observer]; `null` stops forwarding. */
    fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) = Unit
}
