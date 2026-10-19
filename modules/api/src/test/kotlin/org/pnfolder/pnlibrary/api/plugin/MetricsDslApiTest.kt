package org.pnfolder.pnlibrary.api.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class MetricsDslApiTest {

    @Test
    fun `plugin metrics configuration exposes only the shared DSL entry point`() {
        val methods = PluginBuilder::class.java.methods.filter { it.name == "metrics" }
        assertEquals(1, methods.size)
        assertEquals(listOf(Consumer::class.java), methods.single().parameterTypes.toList())
    }
}
