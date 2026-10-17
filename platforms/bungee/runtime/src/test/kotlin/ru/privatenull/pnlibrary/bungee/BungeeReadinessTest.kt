package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.scheduler.TaskScheduler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class BungeeReadinessTest {
    @Test
    fun `startup callbacks wait for runtime publication and are released once`() {
        val plugin = mock(Plugin::class.java)
        val proxy = mock(ProxyServer::class.java)
        val scheduler = mock(TaskScheduler::class.java)
        `when`(plugin.proxy).thenReturn(proxy)
        `when`(proxy.scheduler).thenReturn(scheduler)
        `when`(plugin.dataFolder).thenReturn(java.io.File("build/readiness-test"))
        val scheduled = mutableListOf<Runnable>()
        doAnswer { invocation -> scheduled.add(invocation.getArgument(1)); null }
            .`when`(scheduler).runAsync(same(plugin), any(Runnable::class.java))
        val adapter = BungeePlatformAdapter(plugin)
        var calls = 0
        try {
            adapter.whenServerReady(Runnable { calls++ })
            assertTrue(scheduled.isEmpty())
            adapter.markReady()
            adapter.markReady()
            assertEquals(1, scheduled.size)
            scheduled.removeAt(0).run()
            assertEquals(1, calls)
            adapter.whenServerReady(Runnable { calls++ })
            assertEquals(1, scheduled.size)
            scheduled.removeAt(0).run()
            assertEquals(2, calls)
        } finally { adapter.close() }
        adapter.whenServerReady(Runnable { calls++ })
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun `closing before readiness discards pending startup callbacks`() {
        val plugin = mock(Plugin::class.java)
        `when`(plugin.dataFolder).thenReturn(java.io.File("build/readiness-test"))
        val adapter = BungeePlatformAdapter(plugin)
        adapter.whenServerReady(Runnable { fail("Closed adapter must not release startup work") })
        adapter.close()
        adapter.markReady()
        verify(plugin, never()).proxy
    }
}
