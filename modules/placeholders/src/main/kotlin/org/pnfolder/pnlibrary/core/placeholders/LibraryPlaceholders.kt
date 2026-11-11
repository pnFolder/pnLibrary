package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity

/** The library's own placeholders, `pnlibrary/main:...`, readable by every plugin. */
class LibraryPlaceholders(
    private val hub: PlaceholderHub,
    private val startedAtMillis: Long,
    private val pluginCount: () -> Int,
    private val statistics: () -> Map<String, Long>,
) {
    fun register() {
        hub.library("version", String::class.java) { PnLibraryIdentity.VERSION }
        hub.library("plugins", Int::class.javaObjectType, pluginCount)
        hub.library("uptime", Long::class.javaObjectType) { (System.currentTimeMillis() - startedAtMillis) / MILLIS_PER_SECOND }
        STATISTICS.forEach { key -> hub.library("stats.$key", Long::class.javaObjectType) { statistics()[key] ?: 0L } }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        val STATISTICS = listOf("errors", "warnings", "tasks", "tasks.failed", "events")
    }
}
