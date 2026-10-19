package org.pnfolder.pnlibrary.spi.platform

/** Builds the common diagnostic envelope shared by every platform adapter. */
class DiagnosticDetailsCollector(
    private val snapshotProvider: () -> PlatformSnapshot,
) {
    /** Collects common details and lets the platform add its native sections. */
    fun collect(
        includeSensitive: Boolean,
        enrich: (MutableMap<String, Any?>) -> Unit,
    ): Map<String, Any?> {
        val details = snapshotProvider().asMap().toMutableMap()
        details["privacy"] = linkedMapOf(
            "sensitiveDataIncluded" to includeSensitive,
            "omittedFields" to if (includeSensitive) emptyList<String>() else listOf("backendAddresses"),
        )

        enrich(details)
        return details
    }
}
