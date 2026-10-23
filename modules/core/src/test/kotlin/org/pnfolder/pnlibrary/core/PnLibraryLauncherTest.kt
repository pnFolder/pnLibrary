package org.pnfolder.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer
import org.pnfolder.pnlibrary.api.expansions.AbstractExpansion
import org.pnfolder.pnlibrary.api.expansions.ExpansionState
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import org.pnfolder.pnlibrary.core.runtime.PnLibraryLauncher
import org.pnfolder.pnlibrary.core.runtime.platform.toRuntimeContext
import org.pnfolder.pnlibrary.localization.MinecraftLocalization
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

class PnLibraryLauncherTest {

    @Test
    fun `bootstrap creates one process runtime`() {
        val dummyOwner = DummyPlugin("pnMarket")
        val dummyPlatform = DummyPlatformAdapter()

        val lib1 = PnLibraryLauncher.bootstrap(dummyOwner, dummyPlatform.toRuntimeContext())
        val lib2 = PnLibraryLauncher.bootstrap(DummyPlugin("another"), DummyPlatformAdapter().toRuntimeContext())

        assertSame(lib1, lib2)
        assertEquals(dummyOwner, lib1.owner)
        assertSame(lib1, dummyPlatform.boundLibrary)

        lib1.close()
        assertTrue(lib1.isClosed)
        assertEquals(null, PnLibraryProvider.getOrNull())
    }

    @Test
    fun `the localization loader is neither a service nor a public expansion`() {
        val library = PnLibraryLauncher.bootstrap(DummyPlugin("pnMarket"), DummyPlatformAdapter().toRuntimeContext())
        try {
            assertNull(library.services.get(MinecraftLocalization::class.java))
            assertNull(library.expansions.get("localization"))
        } finally {
            library.close()
        }
    }

    @Test
    fun `the library wide expansions register and list and hold nothing of the library's own`() {
        val library = PnLibraryLauncher.bootstrap(DummyPlugin("pnMarket"), DummyPlatformAdapter().toRuntimeContext())
        try {
            assertNull(library.expansions.get("localization"))
            assertTrue(library.expansions.all().isEmpty())
            assertNull(library.services.get(MinecraftLocalization::class.java))

            val weather = object : AbstractExpansion("weather") {
                override fun onEnable() = Unit
                override fun onDisable() = Unit
            }
            val handle = library.expansions.register(weather)
            assertSame(weather, library.expansions.get("weather"))
            assertSame(weather, library.expansions.get(weather.javaClass))
            assertEquals(ExpansionState.ENABLED, weather.state)

            handle.close()
            assertNull(library.expansions.get("weather"))
            assertEquals(ExpansionState.DISABLED, weather.state)
        } finally {
            library.close()
        }
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
        override val dataFolder: Path = Files.createTempDirectory("pnlibrary-bootstrap-test")
        override fun bind(library: PnLibrary) {
            assertSame(library, PnLibraryProvider.get())
            boundLibrary = library
        }
        override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> = mapOf("test" to true)
        override fun executeGlobal(task: Runnable) { task.run() }
        override fun executeReply(recipient: Any, task: Runnable) { task.run() }
        override fun close() {}
    }
}
