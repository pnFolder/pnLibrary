package ru.privatenull.pnlibrary.api.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.function.Supplier

class DiagnosticApiConsistencyTest {
    @Test
    fun `built declarations and collected snapshots are immutable`() {
        val source = linkedMapOf<String, Any?>("state" to "ready")
        val configuration = DiagnosticConfiguration.builder("config.yml")
            .exclude("database.password")
            .build()
        val container = DiagnosticContainer.builder("example")
            .snapshot(Supplier { source })
            .configuration(configuration)
            .build()

        source["state"] = "changed"
        val snapshot = container.collect()
        source["later"] = true

        assertEquals("changed", snapshot["state"])
        assertEquals(null, snapshot["later"])
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot as MutableMap<String, Any?>)["other"] = true
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (configuration.excludedPaths as MutableList<String>).add("token")
        }
    }
}
