package org.pnfolder.pnlibrary.api.text

import java.time.Duration

/**
 * Immutable limits for the parsed-component cache.
 *
 * @property scope ownership boundary for cached entries; [ComponentCacheScope.NONE]
 * disables caching
 * @property maximumEntries maximum number of retained entries
 * @property expireAfterAccess idle time after which an entry may be evicted
 * @throws IllegalArgumentException when either limit is not positive
 */
data class ComponentCachePolicy @JvmOverloads constructor(
    val scope: ComponentCacheScope = ComponentCacheScope.PLUGIN,
    val maximumEntries: Int = 1_000,
    val expireAfterAccess: Duration = Duration.ofMinutes(30),
) {
    init {
        require(maximumEntries > 0) { "Cache maximumEntries must be positive" }
        require(!expireAfterAccess.isZero && !expireAfterAccess.isNegative) { "Cache expireAfterAccess must be positive" }
    }
}
