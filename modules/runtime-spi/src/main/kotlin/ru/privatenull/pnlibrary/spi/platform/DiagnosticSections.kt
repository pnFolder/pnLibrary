package ru.privatenull.pnlibrary.spi.platform

import kotlin.math.ceil

/**
 * Building blocks shared by every platform's diagnostic sections, so the same fact is computed the same way
 * on Bukkit, BungeeCord and Velocity.
 */
object DiagnosticSections {
    /** A player latency at or above this value (milliseconds, 95th percentile) raises a health signal. */
    const val HIGH_PING_THRESHOLD_MS: Long = 300

    /**
     * Summarizes player latencies in milliseconds.
     *
     * The 95th percentile uses the nearest-rank method, so a small population with one slow player still
     * reports that player's latency.
     */
    @JvmStatic
    fun pingSummary(pings: Collection<Long>): Map<String, Any?> = linkedMapOf(
        "samples" to pings.size,
        "min" to pings.minOrNull(),
        "average" to pings.average().takeIf { pings.isNotEmpty() },
        "max" to pings.maxOrNull(),
        "p95" to percentile(pings, 0.95),
    )

    /** Nearest-rank percentile of [values], or null when there are none. */
    @JvmStatic
    fun percentile(values: Collection<Long>, fraction: Double): Long? {
        require(fraction in 0.0..1.0) { "fraction must be within 0..1" }
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = ceil(sorted.size * fraction).toInt() - 1
        return sorted[rank.coerceIn(0, sorted.lastIndex)]
    }

    /**
     * Lists plugins that require a plugin which is not installed.
     *
     * @param required hard dependencies of each installed plugin, keyed by the plugin's own identifier
     * @param installed identifiers that are present on the server
     */
    @JvmStatic
    fun missingDependencies(required: Map<String, Collection<String>>, installed: Set<String>): List<Map<String, Any?>> =
        required.mapNotNull { (plugin, dependencies) ->
            val missing = dependencies.filterNot(installed::contains).sorted()
            missing.takeIf { it.isNotEmpty() }?.let { linkedMapOf("plugin" to plugin, "missing" to it) }
        }

    /** Signals every platform reports: unmet dependencies and slow players. */
    @JvmStatic
    fun commonSignals(
        missingDependencies: List<Map<String, Any?>>,
        pings: Collection<Long>,
    ): MutableList<Map<String, Any?>> {
        val signals = mutableListOf<Map<String, Any?>>()
        if (missingDependencies.isNotEmpty()) {
            signals += linkedMapOf("code" to "missingRequiredDependencies", "count" to missingDependencies.size)
        }
        val p95 = percentile(pings, 0.95)
        if (p95 != null && p95 >= HIGH_PING_THRESHOLD_MS) {
            signals += linkedMapOf("code" to "highPlayerLatency", "p95Ms" to p95)
        }
        return signals
    }

    /** The `health` section: `healthy` without signals, otherwise `attention` with the signals. */
    @JvmStatic
    fun health(signals: List<Map<String, Any?>>): Map<String, Any?> = linkedMapOf(
        "status" to if (signals.isEmpty()) "healthy" else "attention",
        "signals" to signals,
    )
}
