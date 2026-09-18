package ru.privatenull.pnlibrary.api.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PluginIdTest {
    @Test
    fun `IDs are normalized and compare by value`() {
        assertEquals(PluginId.of("pnClans"), PluginId.of(" PNCLANS "))
        assertEquals("pnclans", PluginId.of("pnClans").value)
    }

    @Test
    fun `invalid IDs are rejected`() {
        listOf("", "-plugin", "plugin name", "plugin/one", "a".repeat(65)).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { PluginId.of(value) }
        }
    }
}
