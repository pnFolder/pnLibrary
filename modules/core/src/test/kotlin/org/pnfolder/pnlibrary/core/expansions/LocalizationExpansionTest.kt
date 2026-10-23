package org.pnfolder.pnlibrary.core.expansions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.expansions.ExpansionState
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.localization.MinecraftLocale
import org.pnfolder.pnlibrary.localization.TranslationException
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException

class LocalizationExpansionTest {
    @TempDir
    lateinit var cache: Path

    private val expansion by lazy { LocalizationExpansion(cache) }
    private val view by lazy { expansion.bind(Unbound) }

    /** Reaches the live loader without touching the network: an unknown version is rejected before any download. */
    private fun liveReason(): TranslationException.Reason {
        val failure = assertThrows<ExecutionException> {
            view.availableLocales(MinecraftVersion.UNKNOWN).toCompletableFuture().get()
        }
        return (failure.cause as TranslationException).reason
    }

    private fun closedReasonOf(call: () -> Unit) = assertThrows<TranslationException>(call).reason

    @Test
    fun `starting downloads and stores nothing`() {
        expansion.enable()

        assertEquals(ExpansionState.ENABLED, expansion.state)
        Files.walk(cache).use { files -> assertTrue(files.noneMatch(Files::isRegularFile)) }
    }

    @Test
    fun `a plugin closing its view does not stop the loader`() {
        expansion.enable()

        view.close()
        view.layouts.close()

        assertEquals(TranslationException.Reason.UNSUPPORTED_VERSION, liveReason())
        assertEquals(ExpansionState.ENABLED, expansion.state)
    }

    @Test
    fun `disabling closes the loader and enabling restores it`() {
        expansion.enable()
        expansion.disable()

        assertEquals(TranslationException.Reason.CLOSED, closedReasonOf { view.availableLocales(MinecraftVersion.V1_20_4) })

        expansion.enable()
        assertEquals(TranslationException.Reason.UNSUPPORTED_VERSION, liveReason())
    }

    @Test
    fun `registered layouts survive disabling and are dropped when the expansion closes`() {
        val layout = KeyboardLayout.of("expansion-test", "abcdefghijklmnopqrstuvwxyz012345678")
        expansion.enable()
        view.layouts.register(layout)

        expansion.disable()
        expansion.enable()
        assertTrue(layout in view.layouts.all())

        expansion.close()
        assertEquals(KeyboardLayout.builtIn, view.layouts.all())
    }

    @Test
    fun `layouts a view registers belong to its module`() {
        val layout = KeyboardLayout.of("expansion-owned", "abcdefghijklmnopqrstuvwxyz012345678")
        val owned = mutableListOf<AutoCloseable>()
        val module = object : ModuleBinding {
            override fun <R : AutoCloseable> own(acquire: () -> R): R = acquire().also { owned += it }
        }
        expansion.enable()

        expansion.bind(module).layouts.register(layout)
        assertEquals(1, owned.size)
        assertTrue(layout in view.layouts.all())

        owned.single().close()
        assertEquals(KeyboardLayout.builtIn, view.layouts.all())
    }

    @Test
    fun `default lookups go through the view`() {
        assertEquals(
            TranslationException.Reason.CLOSED,
            closedReasonOf { view.locale(MinecraftVersion.V1_20_4, MinecraftLocale.RU_RU) },
        )
    }

    /** A binding whose module never closes. */
    private object Unbound : ModuleBinding {
        override fun <R : AutoCloseable> own(acquire: () -> R): R = acquire()
    }
}
