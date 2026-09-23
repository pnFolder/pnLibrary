package ru.privatenull.pnlibrary.api.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ModuleIdTest {
    @Test
    fun `IDs normalize and compare by value`() {
        assertEquals(ModuleId.of("core"), ModuleId.of(" CORE "))
        assertEquals("core", ModuleId.of(" CORE ").value)
    }

    @Test
    fun `invalid IDs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ModuleId.of("bad id") }
        assertThrows(IllegalArgumentException::class.java) { ModuleId.of("") }
        assertThrows(IllegalArgumentException::class.java) { ModuleId.of("a".repeat(65)) }
    }
}
