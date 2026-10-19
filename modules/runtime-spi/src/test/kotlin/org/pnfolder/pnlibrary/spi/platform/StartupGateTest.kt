package org.pnfolder.pnlibrary.spi.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartupGateTest {
    private val lifecycle = PlatformLifecycle()
    private val dispatched = mutableListOf<String>()
    private val gate = StartupGate(lifecycle) { task -> task.run() }

    private fun task(name: String) = Runnable { dispatched += name }

    @Test
    fun `queued work is released once in submission order`() {
        gate.whenReady(task("a"))
        gate.whenReady(task("b"))
        assertEquals(emptyList<String>(), dispatched)

        gate.markReady()
        gate.markReady()
        assertEquals(listOf("a", "b"), dispatched)
    }

    @Test
    fun `work submitted after readiness is dispatched immediately`() {
        gate.markReady()
        gate.whenReady(task("late"))
        assertEquals(listOf("late"), dispatched)
    }

    @Test
    fun `closed lifecycle drops queued and new work`() {
        gate.whenReady(task("queued"))
        lifecycle.close()
        gate.markReady()
        gate.whenReady(task("after"))
        assertEquals(emptyList<String>(), dispatched)
    }

    @Test
    fun `discarded work is never released`() {
        gate.whenReady(task("discarded"))
        gate.discardPending()
        gate.markReady()
        assertEquals(emptyList<String>(), dispatched)
    }
}
