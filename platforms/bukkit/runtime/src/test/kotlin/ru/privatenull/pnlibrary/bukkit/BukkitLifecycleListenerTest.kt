package ru.privatenull.pnlibrary.bukkit

import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.atomic.AtomicInteger

class BukkitLifecycleListenerTest {

    @Test
    fun `callbacks queued before readiness run once when server becomes ready`() {
        val listener = BukkitLifecycleListener(mock<Plugin>(), null)
        val calls = AtomicInteger()

        listener.whenServerReady(Runnable { calls.incrementAndGet() })
        assertEquals(0, calls.get())

        markServerReady(listener)
        assertEquals(1, calls.get())

        markServerReady(listener)
        assertEquals(1, calls.get())
    }

    @Test
    fun `callbacks registered after readiness run immediately`() {
        val listener = BukkitLifecycleListener(mock<Plugin>(), null)
        markServerReady(listener)
        val calls = AtomicInteger()

        listener.whenServerReady(Runnable { calls.incrementAndGet() })

        assertEquals(1, calls.get())
    }

    @Test
    fun `close discards callbacks that have not run`() {
        val listener = BukkitLifecycleListener(mock<Plugin>(), null)
        val calls = AtomicInteger()

        listener.whenServerReady(Runnable { calls.incrementAndGet() })
        listener.close()
        markServerReady(listener)

        assertEquals(0, calls.get())
    }

    private fun markServerReady(listener: BukkitLifecycleListener) {
        val method = BukkitLifecycleListener::class.java.getDeclaredMethod("markServerReady")
        method.isAccessible = true
        method.invoke(listener)
    }
}
