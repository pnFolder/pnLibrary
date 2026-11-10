package org.pnfolder.pnlibrary.core.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.placeholders.ExternalPlaceholderRegistration
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterCapabilities
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterState
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderCachePolicy
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderKey
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderPublication
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRequest
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderResolver
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class PlaceholderMainThreadTest {
    private val owner = PluginId.of("demo")
    private val player = UUID.randomUUID()

    private class Capture : PlaceholderAdapter {
        override val id = "papi"
        override val state = PlaceholderAdapterState.AVAILABLE
        override val capabilities = PlaceholderAdapterCapabilities()
        lateinit var resolver: PlaceholderResolver<Any>
        override fun publish(
            owner: PluginId,
            key: String,
            resolver: PlaceholderResolver<Any>,
            publication: PlaceholderPublication,
        ): ExternalPlaceholderRegistration {
            this.resolver = resolver
            return object : ExternalPlaceholderRegistration {
                override val state = PlaceholderAdapterState.REGISTERED
                override fun close() = Unit
            }
        }
    }

    private fun slowEntry(resolves: AtomicInteger, millis: Long) = PlaceholderEntry(
        owner = owner,
        key = PlaceholderKey.of("slow", String::class.java),
        resolver = {
            resolves.incrementAndGet()
            CompletableFuture.supplyAsync { Thread.sleep(millis); "fresh" }
        },
        updater = null,
        access = PlaceholderAccess.shared(),
        updateAccess = PlaceholderAccess.ownerOnly(),
        cachePolicy = PlaceholderCachePolicy.none(),
        fallback = "fallback",
        adapterLookup = { null },
    )

    private fun request(waitMillis: Long) =
        PlaceholderRequest(owner, owner, player, emptyMap(), mapOf(PlaceholderAdapter.WAIT_MILLIS_KEY to waitMillis))

    @Test
    fun `zero wait answers at once with the fallback and the next ask gets the fresh value`() {
        val resolves = AtomicInteger()
        val entry = slowEntry(resolves, 200)
        val adapter = Capture()
        entry.publish(PlaceholderPublication("papi"))
        entry.attach(adapter)
        adapter.resolver.resolve(request(0)) // warm the JIT paths so the timing below measures waiting only

        val started = System.nanoTime()
        val first = adapter.resolver.resolve(request(0))
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000.0

        assertEquals("fallback", first)
        assertTrue(elapsedMillis < 5, "answered in $elapsedMillis ms")
        val deadline = System.nanoTime() + 5_000_000_000L
        var later: Any? = first
        while (later != "fresh" && System.nanoTime() < deadline) {
            Thread.sleep(10)
            later = adapter.resolver.resolve(request(0))
        }
        assertEquals("fresh", later)
    }

    @Test
    fun `a warm-up still running is not started again`() {
        val resolves = AtomicInteger()
        val entry = slowEntry(resolves, 300)
        val adapter = Capture()
        entry.publish(PlaceholderPublication("papi"))
        entry.attach(adapter)

        repeat(200) { adapter.resolver.resolve(request(0)) }

        assertEquals(1, resolves.get())
    }

    @Test
    fun `a positive wait still waits for the value`() {
        val entry = slowEntry(AtomicInteger(), 20)
        val adapter = Capture()
        entry.publish(PlaceholderPublication("papi"))
        entry.attach(adapter)

        assertEquals("fresh", adapter.resolver.resolve(request(2_000)))
    }

    @Test
    fun `text without a percent sign never reaches the outside systems`() {
        val renders = AtomicInteger()
        val adapter = object : PlaceholderAdapter {
            override val id = "counting"
            override val state = PlaceholderAdapterState.AVAILABLE
            override val capabilities = PlaceholderAdapterCapabilities()
            override fun render(playerId: UUID?, text: String): String { renders.incrementAndGet(); return text.replace("%x%", "X") }
            override fun publish(owner: PluginId, key: String, resolver: PlaceholderResolver<Any>, publication: PlaceholderPublication) =
                error("unused")
        }
        val hub = PlaceholderHub(TestPlatform)
        val scope = hub.scope(owner, false)
        scope.adapters().register(adapter)

        repeat(100) { assertEquals("plain text", scope.render("plain text", null).toCompletableFuture().get()) }
        assertEquals(0, renders.get())
        assertEquals("a X b", scope.render("a %x% b", null).toCompletableFuture().get())
        assertEquals(1, renders.get())
    }

    private object TestPlatform : PlatformAdapter {
        override val type = PlatformType.BUKKIT
        override val dataFolder = Files.createTempDirectory("pnlibrary-placeholder-test")
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = emptyMap()
        override fun executeGlobal(task: Runnable) = task.run()
        override fun executeReply(recipient: Any, task: Runnable) = task.run()
        override fun close() = Unit
    }
}
