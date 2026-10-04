package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MetricsBuilderTest {
    @Test
    fun `builds independent bStats and FastStats configurations`() {
        val configurations = MetricsBuilder().apply {
            bStats(32592)
            fastStats("token")
        }.build()

        assertEquals(
            listOf(MetricsProvider.BSTATS, MetricsProvider.FASTSTATS),
            configurations.map { it.provider },
        )
        assertEquals(32592, configurations[0].projectId)
        assertEquals("token", configurations[1].token)
    }

    @Test
    fun `rejects empty metrics configuration`() {
        assertThrows(IllegalArgumentException::class.java) { MetricsBuilder().build() }
    }
}
