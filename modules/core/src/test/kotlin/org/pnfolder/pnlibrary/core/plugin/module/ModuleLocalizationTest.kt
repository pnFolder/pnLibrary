package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.expansions.ExpansionState
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.core.expansions.ExpansionManagerImpl
import org.pnfolder.pnlibrary.core.expansions.LocalizationExpansion
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryImplTest.Companion.registerModule
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryImplTest.Companion.registry
import org.pnfolder.pnlibrary.localization.MinecraftLocalization
import org.pnfolder.pnlibrary.localization.TranslationException
import org.pnfolder.pnlibrary.localization.TranslationIndex
import org.pnfolder.pnlibrary.localization.TranslationMatch
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout
import java.nio.file.Path
import java.util.concurrent.ExecutionException

class ModuleLocalizationTest {
    @TempDir
    lateinit var cache: Path

    private val expansions = ExpansionManagerImpl { _, error -> throw error }
    private val library = ExpansionManagerImpl { _, error -> throw error }
    private lateinit var localization: LocalizationExpansion
    private val dvorak = KeyboardLayout.of("module-test-dvorak", "',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwv`[")
    private val owner = Any()
    private val registry by lazy { registry(expansions = expansions, libraryExpansions = library) }

    @BeforeEach
    fun start() {
        localization = LocalizationExpansion(cache)
        library.register(localization)
    }

    @AfterEach
    fun stop() {
        expansions.close()
        library.close()
    }

    private fun module(id: String): ModuleContext = registry.registerModule(owner, id) { }

    /** A one-entry English index reading queries against the layouts the module's view exposes. */
    private fun apples(context: ModuleContext) =
        TranslationIndex.of(listOf(TranslationMatch("item.apple", "Apple", "apple")), context.localization.layouts)

    @Test
    fun `a layout one module registers is read by another until the first module closes`() {
        val alpha = module("alpha")
        val beta = module("beta")
        val applesOfBeta = apples(beta)
        assertNull(applesOfBeta.best("arrpd"))

        alpha.localization.layouts.register(dvorak)
        assertEquals("item.apple", applesOfBeta.best("arrpd")?.key)

        alpha.close()
        assertNull(applesOfBeta.best("arrpd"))
        assertEquals(KeyboardLayout.builtIn, beta.localization.layouts.all())
    }

    @Test
    fun `closing a module keeps the layouts of the others`() {
        val alpha = module("alpha")
        val beta = module("beta")
        val other = KeyboardLayout.of("module-test-other", "abcdefghijklmnopqrstuvwxyz012345678")
        alpha.localization.layouts.register(dvorak)
        beta.localization.layouts.register(other)

        alpha.close()

        assertEquals(KeyboardLayout.builtIn + other, beta.localization.layouts.all())
    }

    @Test
    fun `layout ids stay unique across modules`() {
        val alpha = module("alpha")
        val beta = module("beta")
        alpha.localization.layouts.register(dvorak)

        assertThrows<IllegalArgumentException> { beta.localization.layouts.register(dvorak) }
        assertThrows<IllegalArgumentException> { alpha.localization.layouts.register(dvorak) }
        assertThrows<IllegalArgumentException> { beta.localization.layouts.register(KeyboardLayout.RU) }
    }

    @Test
    fun `the handle removes a layout early and the module may register it again`() {
        val alpha = module("alpha")
        val handle = alpha.localization.layouts.register(dvorak)

        handle.close()
        handle.close()
        assertEquals(KeyboardLayout.builtIn, alpha.localization.layouts.all())

        alpha.localization.layouts.register(dvorak)
        assertTrue(dvorak in alpha.localization.layouts.all())
    }

    @Test
    fun `a module cannot close the shared loader through its view`() {
        val alpha = module("alpha")
        val beta = module("beta")

        alpha.localization.close()
        alpha.localization.layouts.close()

        assertEquals(ExpansionState.ENABLED, localization.state)
        val failure = assertThrows<ExecutionException> {
            beta.localization.availableLocales(MinecraftVersion.UNKNOWN).toCompletableFuture().get()
        }
        assertEquals(TranslationException.Reason.UNSUPPORTED_VERSION, (failure.cause as TranslationException).reason)
    }

    @Test
    fun `localization is reachable only through the module context`() {
        val alpha = module("alpha")

        assertNull(alpha.services.get(MinecraftLocalization::class.java))
        assertTrue(alpha.services.all(MinecraftLocalization::class.java).isEmpty())
        assertSame(alpha.localization, alpha.localization)
        assertNotSame(alpha.localization, module("beta").localization)
    }

    @Test
    fun `the library's localization expansion is invisible and uncontrollable to a plugin`() {
        val alpha = module("alpha")

        assertNull(alpha.expansions.get("localization"))
        assertNull(alpha.expansions.get(LocalizationExpansion::class.java))
        assertTrue(alpha.expansions.all().isEmpty())
        assertEquals(ExpansionState.ENABLED, localization.state)
        assertSame(localization, library.get("localization"))
    }

    @Test
    fun `a view reports closed once the library has shut down`() {
        val alpha = module("alpha")
        val view = alpha.localization

        library.close()

        assertEquals(TranslationException.Reason.CLOSED, assertThrows<TranslationException> { view.availableVersions() }.reason)
        assertThrows<IllegalStateException> { view.layouts.register(dvorak) }
    }

    @Test
    fun `a closed module has no localization`() {
        val alpha = module("alpha")
        val view = alpha.localization
        alpha.close()

        assertThrows<IllegalStateException> { alpha.localization }
        assertThrows<IllegalStateException> { view.layouts.register(dvorak) }
    }

    @Test
    fun `a platform without a loader says so`() {
        val bare = registry().registerModule(owner, "alpha") { }

        assertEquals(
            "Localization is not available on this platform",
            assertThrows<UnsupportedOperationException> { bare.localization }.message,
        )
    }
}
