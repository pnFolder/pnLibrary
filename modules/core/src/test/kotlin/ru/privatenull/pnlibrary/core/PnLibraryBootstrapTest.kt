package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.runtime.PnLibraryBootstrap


import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import java.util.function.Supplier

class PnLibraryBootstrapTest {

    @Test
    fun `bootstrap creates one process runtime`() {
        val dummyOwner = DummyPlugin("pnMarket")
        val dummyPlatform = DummyPlatformAdapter()

        val lib1 = PnLibraryBootstrap.bootstrap(dummyOwner, dummyPlatform)
        val lib2 = PnLibraryBootstrap.bootstrap(DummyPlugin("another"), DummyPlatformAdapter())

        assertSame(lib1, lib2)
        assertEquals(dummyOwner, lib1.owner)
        assertSame(lib1, dummyPlatform.boundLibrary)

        lib1.close()
        assertTrue(lib1.isClosed)
        assertEquals(null, PnLibraryProvider.getOrNull())
    }

    @Test
    fun `diagnostics container failure is isolated`() {
        val registry = DiagnosticsRegistry()

        // Container 1 succeeds
        registry.register("pnMarket", DiagnosticContainer.builder("auction")
            .snapshot(Supplier { mapOf("lots" to 10) })
            .build())

        // Container 2 throws an exception
        registry.register("pnMarket", DiagnosticContainer.builder("broken")
            .snapshot(Supplier { throw RuntimeException("Database error") })
            .build())

        val snap = registry.snapshot("pnMarket")
        @Suppress("UNCHECKED_CAST")
        val pluginData = (snap["pnmarket"] ?: snap["pnMarket"]) as? Map<String, Any?>
        assertNotNull(pluginData)
        @Suppress("UNCHECKED_CAST")
        val contribs = pluginData!!["contributors"] as Map<String, Any?>

        assertNotNull(contribs["auction"])
        assertNotNull(contribs["broken"])

        @Suppress("UNCHECKED_CAST")
        val brokenData = contribs["broken"] as Map<String, Any?>
        assertTrue(brokenData.containsKey("collectionError"))
    }

    private class DummyPlugin(val name: String)
    private class DummyPlatformAdapter : PlatformAdapter {
        var boundLibrary: PnLibrary? = null
        override val type = PlatformType.BUKKIT
        override val id: String get() = "dummy"
        override fun bind(library: PnLibrary) {
            assertSame(library, PnLibraryProvider.get())
            boundLibrary = library
        }
        override fun details(): Map<String, Any?> = mapOf("test" to true)
        override fun executeGlobal(task: Runnable) { task.run() }
        override fun executeReply(recipient: Any, task: Runnable) { task.run() }
        override fun close() {}
    }
}
