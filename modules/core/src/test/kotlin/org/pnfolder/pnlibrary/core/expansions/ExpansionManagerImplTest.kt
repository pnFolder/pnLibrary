package org.pnfolder.pnlibrary.core.expansions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pnfolder.pnlibrary.api.expansions.AbstractExpansion
import org.pnfolder.pnlibrary.api.expansions.ExpansionState

class ExpansionManagerImplTest {
    private val events = mutableListOf<String>()
    private val failures = mutableListOf<String>()
    private val manager = ExpansionManagerImpl { expansion, error -> failures += "${expansion.id}: ${error.message}" }

    private inner class Probe(
        id: String,
        private val failOnEnable: Boolean = false,
        private val failOnDisable: Boolean = false,
    ) : AbstractExpansion(id) {
        override fun onEnable() {
            if (failOnEnable) error("enable failed")
            events += "enable $id"
        }

        override fun onDisable() {
            events += "disable $id"
            if (failOnDisable) error("disable failed")
        }
    }

    @Test
    fun `registering enables the expansion once`() {
        val probe = Probe("a")
        manager.register(probe)
        probe.enable()

        assertEquals(ExpansionState.ENABLED, probe.state)
        assertEquals(listOf("enable a"), events)
        assertSame(probe, manager.get("a"))
        assertSame(probe, manager.get(Probe::class.java))
    }

    @Test
    fun `a duplicate id is rejected and the first expansion is untouched`() {
        val first = Probe("a")
        manager.register(first)

        assertThrows<IllegalStateException> { manager.register(Probe("a")) }
        assertSame(first, manager.get("a"))
        assertEquals(ExpansionState.ENABLED, first.state)
    }

    @Test
    fun `closing the handle unregisters and disables once`() {
        val probe = Probe("a")
        val handle = manager.register(probe)

        handle.close()
        handle.close()

        assertNull(manager.get("a"))
        assertEquals(ExpansionState.DISABLED, probe.state)
        assertEquals(listOf("enable a", "disable a"), events)
    }

    @Test
    fun `a failing expansion is reported and does not stop the others`() {
        val broken = Probe("broken", failOnEnable = true)
        val healthy = Probe("healthy")

        manager.register(broken)
        manager.register(healthy)

        assertEquals(ExpansionState.FAILED, broken.state)
        assertEquals(ExpansionState.ENABLED, healthy.state)
        assertEquals(listOf("broken: enable failed"), failures)
        assertNotNull(manager.get("broken"))
    }

    @Test
    fun `closing stops expansions in reverse order even when one fails`() {
        val first = Probe("a")
        val second = Probe("b", failOnDisable = true)
        val third = Probe("c")
        listOf(first, second, third).forEach(manager::register)
        events.clear()

        assertThrows<IllegalStateException> { manager.close() }

        assertEquals(listOf("disable c", "disable b", "disable a"), events)
        assertEquals(List(3) { ExpansionState.CLOSED }, listOf(first, second, third).map { it.state })
        assertThrows<IllegalStateException> { manager.register(Probe("d")) }
    }
}
