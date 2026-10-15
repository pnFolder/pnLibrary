package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.spi.platform.DiagnosticDetailsCollector
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot

/** Collects the small set of BungeeCord facts that is useful during incident diagnosis. */
internal class BungeeDiagnosticDetailsCollector(
    private val plugin: Plugin,
) {
    fun collect(snapshot: PlatformSnapshot, includeSensitive: Boolean): Map<String, Any?> =
        DiagnosticDetailsCollector { snapshot }.collect(includeSensitive) { details ->
            val proxy = plugin.proxy
            val plugins = proxy.pluginManager.plugins
            val installedNames = plugins.map { it.description.name }.toSet()
            val missingDependencies = plugins.mapNotNull { installedPlugin ->
                val missing = installedPlugin.description.depends
                    .filterNot(installedNames::contains)
                    .sorted()
                missing.takeIf { it.isNotEmpty() }?.let {
                    linkedMapOf("plugin" to installedPlugin.description.name, "missing" to it)
                }
            }
            val backends = proxy.servers.map { (name, info) ->
                linkedMapOf<String, Any?>(
                    "name" to name,
                    "players" to info.players.size,
                ).apply {
                    if (includeSensitive) {
                        this["host"] = info.address.hostString
                        this["port"] = info.address.port
                    }
                }
            }
            val pings = proxy.players.map { it.ping }.filter { it >= 0 }
            val p95 = percentile(pings, 0.95)
            val signals = buildList<Map<String, Any?>> {
                if (missingDependencies.isNotEmpty()) {
                    add(linkedMapOf("code" to "missingRequiredDependencies", "count" to missingDependencies.size))
                }
                if (p95 != null && p95 >= HIGH_PING_THRESHOLD_MS) {
                    add(linkedMapOf("code" to "highPlayerLatency", "p95Ms" to p95))
                }
            }

            details["missingRequiredDependencies"] = missingDependencies
            details["backends"] = backends
            details["players"] = linkedMapOf(
                "online" to proxy.onlineCount,
                "byBackend" to proxy.players
                    .groupingBy { it.server?.info?.name ?: "[unassigned]" }
                    .eachCount(),
                "pingMs" to pingSummary(pings),
            )
            details["health"] = linkedMapOf(
                "status" to if (signals.isEmpty()) "healthy" else "attention",
                "signals" to signals,
            )
        }

    private fun pingSummary(values: List<Int>): Map<String, Any?> = linkedMapOf(
        "min" to values.minOrNull(),
        "average" to values.average().takeIf { values.isNotEmpty() },
        "max" to values.maxOrNull(),
        "p95" to percentile(values, 0.95),
    )

    private fun percentile(values: List<Int>, percentile: Double): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.lastIndex)]
    }

    private companion object {
        const val HIGH_PING_THRESHOLD_MS = 300
    }
}
