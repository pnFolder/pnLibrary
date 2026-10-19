package org.pnfolder.pnlibrary.spi.platform

/** Diagnostic snapshots and native log observation supplied by a platform. */
interface PlatformDiagnostics : PlatformSnapshotProvider, PlatformDiagnosticReport {
    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = snapshot().asMap()
}
