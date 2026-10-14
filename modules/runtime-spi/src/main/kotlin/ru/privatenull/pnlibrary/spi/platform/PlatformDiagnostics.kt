package ru.privatenull.pnlibrary.spi.platform

/** Diagnostic snapshots and native log observation supplied by a platform. */
interface PlatformDiagnostics {
    fun snapshot(): PlatformSnapshot = PlatformSnapshot.unknown("unknown")
    fun details(): Map<String, Any?> = snapshot().asMap()
    fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = details()
}
