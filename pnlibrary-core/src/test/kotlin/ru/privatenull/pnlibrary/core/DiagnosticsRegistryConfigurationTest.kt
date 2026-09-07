package ru.privatenull.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
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
}
