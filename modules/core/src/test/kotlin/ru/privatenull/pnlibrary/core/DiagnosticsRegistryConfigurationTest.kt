package ru.privatenull.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import java.nio.file.Path

class DiagnosticsRegistryConfigurationTest {
    @TempDir lateinit var temporary: Path

    @Test
    fun `all returns configurations from every plugin without merging identical paths`() {
        val registry = DiagnosticsRegistry()
        val first = temporary.resolve("first")
        val second = temporary.resolve("second")
        registry.register("pnMarket", first, DiagnosticContainer.builder("market").configuration("config.yml").build())
        registry.register("pnClans", second, DiagnosticContainer.builder("clans").configuration("config.yml").build())

        val configurations = registry.configurations("all")

        assertEquals(2, configurations.size)
        assertEquals(setOf("pnmarket", "pnclans"), configurations.map { it.plugin }.toSet())
        assertEquals(setOf(first.toAbsolutePath(), second.toAbsolutePath()), configurations.map { it.dataDirectory }.toSet())
    }

    @Test
    fun `diagnostic summary aggregates incidents without collecting contributors`() {
        val registry = DiagnosticsRegistry()

        registry.record("pnMarket", DiagnosticLevel.WARNING, "database", "TIMEOUT", "retrying")
        registry.record("pnMarket", DiagnosticLevel.WARNING, "database", "TIMEOUT", "retrying")
        registry.record("pnMarket", DiagnosticLevel.ERROR, "database", "DOWN", "unavailable")

        val plugin = registry.diagnosticSummary()["plugins"]
            .let { it as List<*> }
            .single() as Map<*, *>
        val incidents = plugin["incidents"] as Map<*, *>

        assertEquals(1, registry.diagnosticSummary()["registeredPlugins"])
        assertEquals(2, incidents["incidentCount"])
        assertEquals(3L, incidents["occurrenceCount"])
        assertEquals(1, (incidents["byLevel"] as Map<*, *>)["WARNING"])
    }

    @Test
    fun `history analytics exposes bounded incident timeline`() {
        val registry = DiagnosticsRegistry()

        registry.record("pnMarket", DiagnosticLevel.WARNING, "database", "TIMEOUT", "retrying")
        registry.record("pnMarket", DiagnosticLevel.ERROR, "database", "DOWN", "unavailable")

        val history = registry.historyAnalytics()

        assertEquals(2, history["incidentCount"])
        assertEquals(2L, history["occurrenceCount"])
        assertEquals(2, (history["recent"] as List<*>).size)
        assertEquals(1, (history["byLevel"] as Map<*, *>) ["ERROR"])
    }
}
