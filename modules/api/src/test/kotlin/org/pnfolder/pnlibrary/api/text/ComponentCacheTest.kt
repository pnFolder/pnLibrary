package org.pnfolder.pnlibrary.api.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.text.cache.ComponentCachePolicy
import org.pnfolder.pnlibrary.api.text.cache.ComponentCacheScope
import org.pnfolder.pnlibrary.api.text.cache.ComponentCacheStatistics

class ComponentCacheTest {
    @Test
    fun `statistics count lookups and the hit rate`() {
        assertEquals(0.0, ComponentCacheStatistics(0, 0, 0, 0).hitRate)
        val stats = ComponentCacheStatistics(hits = 3, misses = 1, evictions = 0, size = 4)
        assertEquals(4, stats.lookups)
        assertEquals(0.75, stats.hitRate)
    }

    @Test
    fun `the disabled policy never caches and limits must be positive`() {
        assertEquals(ComponentCacheScope.NONE, ComponentCachePolicy.DISABLED.scope)
        assertThrows(IllegalArgumentException::class.java) { ComponentCachePolicy(maximumEntries = 0) }
    }
}
