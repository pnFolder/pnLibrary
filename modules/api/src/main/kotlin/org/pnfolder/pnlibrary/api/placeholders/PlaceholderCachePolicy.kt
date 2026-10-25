package org.pnfolder.pnlibrary.api.placeholders

import java.time.Duration

/**
 * Cache policy for one placeholder registration.
 *
 * @property scope invocation dimension used to isolate entries
 * @property maximumEntries maximum retained entries
 * @property expireAfterWrite lifetime measured from insertion
 * @property cacheNullValues whether absent resolver results are cached
 */
data class PlaceholderCachePolicy @JvmOverloads constructor(
    val scope: PlaceholderCacheScope = PlaceholderCacheScope.NONE,
    val maximumEntries: Int = 1_000,
    val expireAfterWrite: Duration = Duration.ofSeconds(30),
    val cacheNullValues: Boolean = false,
) {
    init {
        require(maximumEntries > 0) { "Cache maximumEntries must be positive" }
        require(!expireAfterWrite.isZero && !expireAfterWrite.isNegative) { "Cache expireAfterWrite must be positive" }
    }
    /** Factories for the most common cache policies. */
    companion object {
        /** Creates a policy with caching disabled. */
        @JvmStatic
        fun none(): PlaceholderCachePolicy = PlaceholderCachePolicy()
        /**
         * Creates a per-player policy retaining at most 10,000 entries for [duration].
         *
         * @throws IllegalArgumentException when [duration] is zero or negative
         */
        @JvmStatic
        fun player(duration: Duration): PlaceholderCachePolicy =
            PlaceholderCachePolicy(PlaceholderCacheScope.PLAYER, 10_000, duration)
    }
}
