package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Answers.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import ru.privatenull.pnlibrary.spi.metrics.NoopMetricsFactory
import java.nio.file.Path
import java.util.Optional

class VelocityReadinessTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `startup work waits for ready and is not scheduled twice`() {
        val callbacks = mutableListOf<Runnable>()
        val adapter = adapter(callbacks)
        var calls = 0
        adapter.whenServerReady(Runnable { calls++ })
        assertEquals(0, callbacks.size)
        adapter.markReady()
        adapter.markReady()
        assertEquals(1, callbacks.size)
        callbacks.single().run()
        assertEquals(1, calls)
        adapter.whenServerReady(Runnable { calls++ })
        assertEquals(2, callbacks.size)
        adapter.close()
    }

    @Test
    fun `closed adapter discards pending readiness work`() {
        val callbacks = mutableListOf<Runnable>()
        val adapter = adapter(callbacks)
        adapter.whenServerReady(Runnable {})
        adapter.close()
        adapter.markReady()
        adapter.whenServerReady(Runnable {})
        assertEquals(0, callbacks.size)
    }

    private fun adapter(callbacks: MutableList<Runnable>): VelocityPlatformAdapter {
        val proxy = mock(ProxyServer::class.java, RETURNS_DEEP_STUBS)
        val owner = Any()
        whenever(proxy.pluginManager.fromInstance(owner)).thenReturn(Optional.of(mock()))
        val builder = proxy.scheduler.buildTask(owner, Runnable {})
        whenever(proxy.scheduler.buildTask(eq(owner), any<Runnable>())).thenAnswer {
            callbacks += it.getArgument<Runnable>(1)
            builder
        }
        return VelocityPlatformAdapter(owner, proxy, NoopMetricsFactory, directory, mock(Logger::class.java))
    }
}
