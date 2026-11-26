package org.pnfolder.pnlibrary.api.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.text.cache.ComponentCachePolicy;
import org.pnfolder.pnlibrary.api.text.cache.ComponentCacheScope;
import org.pnfolder.pnlibrary.api.text.cache.ComponentCacheStatistics;

class ComponentCacheTest {
    @Test
    @DisplayName("statistics count lookups and the hit rate")
    void statisticsCountLookupsAndTheHitRate() {
        assertEquals(0.0, new ComponentCacheStatistics(0, 0, 0, 0).getHitRate());
        ComponentCacheStatistics stats = new ComponentCacheStatistics(3, 1, 0, 4);
        assertEquals(4, stats.getLookups());
        assertEquals(0.75, stats.getHitRate());
    }

    @Test
    @DisplayName("the disabled policy never caches and limits must be positive")
    void theDisabledPolicyNeverCachesAndLimitsMustBePositive() {
        assertEquals(ComponentCacheScope.NONE, ComponentCachePolicy.DISABLED.getScope());
        assertThrows(IllegalArgumentException.class, () -> new ComponentCachePolicy(ComponentCacheScope.PLUGIN, 0));
    }
}
