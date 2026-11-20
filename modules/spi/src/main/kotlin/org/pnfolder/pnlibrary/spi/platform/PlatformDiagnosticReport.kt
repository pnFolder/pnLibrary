package org.pnfolder.pnlibrary.spi.platform

/** Produces the privacy-aware native details included in support reports. */
interface PlatformDiagnosticReport {
    /** Native details for support reports; sensitive values are included only when [includeSensitive] is true. */
    fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
}
