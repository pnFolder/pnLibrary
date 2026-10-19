package org.pnfolder.pnlibrary.spi.platform

/** Produces the privacy-aware native details included in support reports. */
interface PlatformDiagnosticReport {
    fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
}
