package org.pnfolder.pnlibrary.api.text.cache

/**
 * Point-in-time component-cache counters.
 *
 * @property hits successful cache lookups
 * @property misses lookups that required parsing
 * @property evictions entries removed by capacity or expiry policy
 * @property size entries retained when the snapshot was created
 */
data class ComponentCacheStatistics(
    val hits: Long, val misses: Long, val evictions: Long, val size: Int,
) {
    /** Number of lookups: hits plus misses. */
    val lookups: Long get() = hits + misses

    /** Ratio of hits to all lookups, or `0.0` before the first lookup. */
    val hitRate: Double get() = if (lookups == 0L) 0.0 else hits.toDouble() / lookups
}
