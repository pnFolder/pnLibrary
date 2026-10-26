package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import java.util.function.Consumer

class ModuleDefinitionMetricsTest {

    @Test
    fun `separate provider declarations retain both backends in either order`() {
        val bStatsFirst = ModuleDefinitionBuilder()
        bStatsFirst.metrics(Consumer { it.bStats(10) })
        bStatsFirst.metrics(Consumer { it.fastStats("test-token") })

        val fastStatsFirst = ModuleDefinitionBuilder()
        fastStatsFirst.metrics(Consumer { it.fastStats("test-token") })
        fastStatsFirst.metrics(Consumer { it.bStats(10) })

        for (definition in listOf(bStatsFirst, fastStatsFirst)) {
            assertEquals(
                setOf(MetricsProvider.BSTATS, MetricsProvider.FASTSTATS),
                definition.metrics.providers.map { it.provider }.toSet(),
            )
            assertEquals(10, definition.metrics.providers.single { it.provider == MetricsProvider.BSTATS }.projectId)
            assertEquals(true, definition.metrics.enabled)
        }
    }

    @Test
    fun `duplicate provider declaration fails without changing existing setup`() {
        val definition = ModuleDefinitionBuilder()
        definition.metrics(Consumer { it.bStats(10) })

        assertThrows(IllegalArgumentException::class.java) { definition.metrics(Consumer { it.bStats(20) }) }
        assertEquals(10, definition.metrics.providers.single().projectId)
        assertEquals(1, definition.metrics.providers.size)
    }
}
