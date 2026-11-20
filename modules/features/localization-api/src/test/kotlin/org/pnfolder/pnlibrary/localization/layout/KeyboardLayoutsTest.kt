package org.pnfolder.pnlibrary.localization.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pnfolder.pnlibrary.localization.TranslationIndex
import org.pnfolder.pnlibrary.localization.TranslationMatch

class KeyboardLayoutsTest {
    private val dvorak = KeyboardLayout.of("test-dvorak", "',.pyfgcrl/=\\aoeuidhtns-;qjkxbmwv`[")
    private val other = KeyboardLayout.of("test-other", "qwertyuiop[]\\asdfghjkl;'zxcvbnm,.`-".reversed())

    private fun apples(layouts: KeyboardLayouts) =
        TranslationIndex.of(listOf(TranslationMatch("item.apple", "Apple", "apple")), layouts)

    @Test
    fun `a new registry holds the built-in layouts`() {
        assertEquals(KeyboardLayout.builtIn, KeyboardLayouts.create().all())
    }

    @Test
    fun `registered layouts follow the built-in ones in registration order`() {
        val layouts = KeyboardLayouts.create()
        layouts.register(dvorak)
        layouts.register(other)

        assertEquals(KeyboardLayout.builtIn + dvorak + other, layouts.all())
    }

    @Test
    fun `an id that is taken is rejected`() {
        val layouts = KeyboardLayouts.create()
        layouts.register(dvorak)

        assertThrows<IllegalArgumentException> { layouts.register(KeyboardLayout.of("test-dvorak", "abcdefghijklmnopqrstuvwxyz012345678")) }
        assertThrows<IllegalArgumentException> { layouts.register(KeyboardLayout.RU) }
        assertEquals(KeyboardLayout.builtIn + dvorak, layouts.all())
    }

    @Test
    fun `closing the handle removes the layout and may repeat`() {
        val layouts = KeyboardLayouts.create()
        val handle = layouts.register(dvorak)

        handle.close()
        handle.close()

        assertEquals(KeyboardLayout.builtIn, layouts.all())
        layouts.register(dvorak)
        assertTrue(dvorak in layouts.all())
    }

    @Test
    fun `a spent handle does not remove a later registration of the same layout`() {
        val layouts = KeyboardLayouts.create()
        val first = layouts.register(dvorak)
        first.close()
        layouts.register(dvorak)

        first.close()

        assertTrue(dvorak in layouts.all())
    }

    @Test
    fun `closing the registry drops registrations and rejects new ones`() {
        val layouts = KeyboardLayouts.create()
        val handle = layouts.register(dvorak)

        layouts.close()
        handle.close()

        assertEquals(KeyboardLayout.builtIn, layouts.all())
        assertThrows<IllegalStateException> { layouts.register(dvorak) }
    }

    @Test
    fun `the built-in registry is read-only and closing it does nothing`() {
        assertThrows<UnsupportedOperationException> { KeyboardLayouts.builtIn.register(dvorak) }
        KeyboardLayouts.builtIn.close()
        assertEquals(KeyboardLayout.builtIn, KeyboardLayouts.builtIn.all())
    }

    @Test
    fun `an index finds text typed on a registered layout without passing layouts`() {
        val layouts = KeyboardLayouts.create()
        val index = apples(layouts)
        assertNull(index.best("arrpd"))

        val handle = layouts.register(dvorak)
        assertEquals("item.apple", index.best("arrpd")?.key)
        assertEquals(listOf("item.apple"), index.suggest("arrpd").map { it.key })

        handle.close()
        assertNull(index.best("arrpd"))
    }

    @Test
    fun `layouts passed to a call replace the registry`() {
        val layouts = KeyboardLayouts.create().also { it.register(dvorak) }
        val index = apples(layouts)

        assertNull(index.best("arrpd", 0.7, emptyList()))
        assertNull(index.best("arrpd", 0.7, KeyboardLayout.builtIn))
        assertEquals("item.apple", index.best("arrpd", 0.7, KeyboardLayout.builtIn + dvorak)?.key)
    }

    @Test
    fun `an index built without a registry uses the built-in layouts`() {
        val index = TranslationIndex.of(listOf(TranslationMatch("item.apple", "Яблоко", "apple")))

        assertSame(KeyboardLayouts.builtIn, index.layouts)
        assertEquals("item.apple", index.best("z,kjrj")?.key)
    }
}
