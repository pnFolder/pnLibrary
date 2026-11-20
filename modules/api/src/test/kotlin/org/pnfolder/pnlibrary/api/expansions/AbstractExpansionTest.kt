package org.pnfolder.pnlibrary.api.expansions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AbstractExpansionTest {
    private class Probe(var failEnable: Boolean = false, var failDisable: Boolean = false) : AbstractExpansion("probe") {
        val calls = mutableListOf<String>()

        override fun onEnable() {
            calls += "enable"
            if (failEnable) error("no enable")
        }

        override fun onDisable() {
            calls += "disable"
            if (failDisable) error("no disable")
        }

        override fun onClose() {
            calls += "close"
        }
    }

    @Test
    fun `enable and disable move the state and call hooks once`() {
        val probe = Probe()
        probe.enable()
        probe.enable()
        assertEquals(ExpansionState.ENABLED, probe.state)
        probe.disable()
        probe.disable()

        assertEquals(ExpansionState.DISABLED, probe.state)
        assertEquals(listOf("enable", "disable"), probe.calls)
    }

    @Test
    fun `a failed enable marks the expansion failed and may be retried`() {
        val probe = Probe(failEnable = true)
        assertThrows(IllegalStateException::class.java) { probe.enable() }
        assertEquals(ExpansionState.FAILED, probe.state)

        probe.failEnable = false
        probe.enable()
        assertEquals(ExpansionState.ENABLED, probe.state)
    }

    @Test
    fun `close releases an enabled expansion then closes it for good`() {
        val probe = Probe()
        probe.enable()
        probe.close()
        probe.close()
        probe.enable()

        assertEquals(ExpansionState.CLOSED, probe.state)
        assertEquals(listOf("enable", "disable", "close"), probe.calls)
    }

    @Test
    fun `closing a disabled expansion skips onDisable`() {
        val probe = Probe()
        probe.close()

        assertEquals(listOf("close"), probe.calls)
    }

    @Test
    fun `a failing onDisable still ends in the target state and closes`() {
        val probe = Probe(failDisable = true)
        probe.enable()
        assertThrows(IllegalStateException::class.java) { probe.close() }

        assertEquals(ExpansionState.CLOSED, probe.state)
        assertEquals(listOf("enable", "disable", "close"), probe.calls)
    }
}
