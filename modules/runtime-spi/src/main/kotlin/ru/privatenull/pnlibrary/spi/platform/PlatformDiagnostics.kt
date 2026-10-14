package ru.privatenull.pnlibrary.spi.platform

/** Diagnostic snapshots and native log observation supplied by a platform. */
interface PlatformDiagnostics : PlatformSnapshotProvider, PlatformDiagnosticReport {
    fun details(): Map<String, Any?> = snapshot().asMap()

    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = details()
}
