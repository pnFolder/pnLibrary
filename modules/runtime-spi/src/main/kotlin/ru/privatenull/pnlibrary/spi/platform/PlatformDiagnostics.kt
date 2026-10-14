package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.logging.LogLevel

/** Diagnostic snapshots and native log observation supplied by a platform. */
interface PlatformDiagnostics {
    fun snapshot(): PlatformSnapshot = PlatformSnapshot.unknown("unknown")
    fun details(): Map<String, Any?> = snapshot().asMap()
    fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = details()
    fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) = Unit
}
