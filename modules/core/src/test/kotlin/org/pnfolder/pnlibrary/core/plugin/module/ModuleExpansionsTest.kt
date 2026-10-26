package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pnfolder.pnlibrary.api.expansions.AbstractExpansion
import org.pnfolder.pnlibrary.api.expansions.ExpansionState
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.core.expansions.ExpansionManagerImpl
import org.pnfolder.pnlibrary.core.expansions.ModuleBinding
import org.pnfolder.pnlibrary.core.expansions.ModuleBoundExpansion
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryImplTest.Companion.registerModule
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryImplTest.Companion.registry
import java.util.concurrent.atomic.AtomicBoolean

class ModuleExpansionsTest {
    private val expansions = ExpansionManagerImpl { _, error -> throw error }
    private val library = ExpansionManagerImpl { _, error -> throw error }
    private val registry by lazy { registry(expansions = expansions, libraryExpansions = library) }
    private val owner = Any()
    private val bound = Bound()

    @BeforeEach
    fun start() {
        library.register(bound)
    }

    @AfterEach
    fun stop() {
        expansions.close()
        library.close()
    }

    private fun module(id: String): ModuleContext = registry.registerModule(owner, id) { }

    private fun ModuleContext.view(): String = (this.expansions as ModuleExpansions).view(Bound::class.java)

    @Test
    fun `a view is bound once per module`() {
        val alpha = module("alpha")
        val beta = module("beta")

        assertSame(alpha.view(), alpha.view())
        assertEquals(1, bound.bindings.size)
        assertNotEquals(alpha.view(), beta.view())
        assertEquals(2, bound.bindings.size)
    }

    @Test
    fun `what a view owns closes with its module and not with another`() {
        val alpha = module("alpha")
        val beta = module("beta")
        val ofAlpha = AtomicBoolean()
        val ofBeta = AtomicBoolean()
        alpha.view()
        beta.view()
        bound.bindings[0].own { Flag(ofAlpha) }
        bound.bindings[1].own { Flag(ofBeta) }

        alpha.close()

        assertTrue(ofAlpha.get())
        assertTrue(!ofBeta.get())
        var acquired = false
        assertThrows<IllegalStateException> { bound.bindings[0].own { acquired = true; Flag(AtomicBoolean()) } }
        assertTrue(!acquired)
    }

    @Test
    fun `an expansion a module registers ends with the module`() {
        val alpha = module("alpha")
        val beta = module("beta")
        val weather = Plain("weather")
        val handle = alpha.expansions.register(weather)
        val stays = Plain("stays")
        beta.expansions.register(stays)

        assertEquals(ExpansionState.ENABLED, weather.state)
        assertSame(weather, beta.expansions.get("weather"))

        alpha.close()

        assertEquals(ExpansionState.DISABLED, weather.state)
        assertNull(expansions.get("weather"))
        assertEquals(ExpansionState.ENABLED, stays.state)
        handle.close()
        assertSame(stays, expansions.get("stays"))
    }

    @Test
    fun `the handle removes an expansion early and its id can be used again`() {
        val alpha = module("alpha")
        val first = Plain("weather")
        val handle = alpha.expansions.register(first)

        handle.close()
        handle.close()
        assertEquals(ExpansionState.DISABLED, first.state)
        assertNull(expansions.get("weather"))

        val second = Plain("weather")
        alpha.expansions.register(second)
        assertSame(second, expansions.get("weather"))
    }

    @Test
    fun `expansion ids are unique across modules and a library expansion is neither listed nor shadowed`() {
        val alpha = module("alpha")
        val beta = module("beta")
        alpha.expansions.register(Plain("weather"))

        assertThrows<IllegalStateException> { beta.expansions.register(Plain("weather")) }
        assertThrows<IllegalStateException> { alpha.expansions.register(Plain("weather")) }

        assertNull(beta.expansions.get("bound"))
        assertEquals(listOf("weather"), beta.expansions.all().map { it.id })
        beta.expansions.register(Plain("bound"))
        assertSame(bound, library.get("bound"))
        assertEquals("view-1", beta.view())
    }

    @Test
    fun `a closed module can neither register nor bind`() {
        val alpha = module("alpha")
        alpha.close()

        assertThrows<IllegalStateException> { alpha.expansions.register(Plain("late")) }
        assertThrows<IllegalStateException> { alpha.view() }
    }

    @Test
    fun `a view of an expansion the runtime lacks says so`() {
        val bare = registry().registerModule(owner, "alpha") { }

        assertEquals("Bound is not available on this platform", assertThrows<UnsupportedOperationException> { bare.view() }.message)
    }

    private class Plain(id: String) : AbstractExpansion(id) {
        override fun onEnable() = Unit
        override fun onDisable() = Unit
    }

    private class Bound : AbstractExpansion("bound"), ModuleBoundExpansion<String> {
        val bindings = mutableListOf<ModuleBinding>()
        override fun bind(module: ModuleBinding): String {
            bindings += module
            return "view-${bindings.size}"
        }

        override fun onEnable() = Unit
        override fun onDisable() = Unit
    }

    private class Flag(private val closed: AtomicBoolean) : AutoCloseable {
        override fun close() = closed.set(true)
    }
}
