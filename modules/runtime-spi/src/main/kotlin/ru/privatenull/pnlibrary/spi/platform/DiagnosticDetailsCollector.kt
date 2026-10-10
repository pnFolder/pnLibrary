package ru.privatenull.pnlibrary.spi.platform

import java.time.Instant

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
        details["collection"] = linkedMapOf(
            "startedUtc" to Instant.now().toString(),
            "thread" to Thread.currentThread().name,
            "includeSensitive" to includeSensitive,
        )
        details["privacy"] = linkedMapOf(
            "sensitiveDataIncluded" to includeSensitive,
            "redactedSections" to if (includeSensitive) {
                emptyList<String>()
            } else {
                listOf("players", "serverHosts", "pluginPaths")
            },
            "playerIdentityFields" to listOf("username", "uuid", "ping", "server"),
        )

        enrich(details)
        return details
    }
}
