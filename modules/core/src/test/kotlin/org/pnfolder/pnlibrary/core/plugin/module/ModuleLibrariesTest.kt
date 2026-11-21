package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import java.net.URLClassLoader

class ModuleLibrariesTest {
    private val hikari = ModuleLibrary.of("com.zaxxer:HikariCP:5.1.0")

    @Test
    fun `a library is fetched once and its isolated loader is kept`() {
        val holder = URLClassLoader(emptyArray())
        var fetched = 0
        val libraries = ModuleLibrariesImpl(null) { fetched++; holder }

        libraries.load(hikari)
        libraries.load(hikari)

        assertEquals(1, fetched)
        assertSame(holder, libraries.classLoader)
        assertEquals(listOf(hikari), libraries.loaded)
    }

    @Test
    fun `a shared library adds no loader and a failure is not remembered as loaded`() {
        val shared = ModuleLibrary.builder("g", "a", "1").scope(org.pnfolder.pnlibrary.api.libraries.LibraryScope.LIBRARY).build()
        val libraries = ModuleLibrariesImpl(null) { if (it == hikari) error("offline") else null }

        libraries.load(shared)
        assertThrows(IllegalStateException::class.java) { libraries.load(hikari) }
        assertEquals(listOf(shared), libraries.loaded)
    }

    @Test
    fun `a module sees the libraries of its plugin and does not fetch them twice`() {
        var fetched = 0
        val plugin = ModuleLibrariesImpl(null) { fetched++; null }
        val module = ModuleLibrariesImpl(plugin) { fetched++; null }
        val own = ModuleLibrary.of("g:own:1")

        plugin.load(hikari)
        module.load(hikari)
        module.load(own)

        assertEquals(2, fetched)
        assertEquals(listOf(own), module.loaded)
        assertEquals(listOf(hikari, own), module.available)
        assertEquals(listOf(hikari), plugin.available)
        assertEquals(hikari, module.find("com.zaxxer", "HikariCP"))
    }

    @Test
    fun `remove takes a library off the list and tells whether it was there`() {
        val libraries = ModuleLibrariesImpl(null) { null }
        libraries.load(hikari)

        assertEquals(true, libraries.remove(hikari))
        assertEquals(false, libraries.remove(hikari))
        assertEquals(emptyList<ModuleLibrary>(), libraries.loaded)
    }

    @Test
    fun `a class is searched in the module loader first and then in the plugin loader`() {
        val plugin = ModuleLibrariesImpl(null) { null }
        val module = ModuleLibrariesImpl(plugin) { null }

        assertSame(String::class.java, module.loadClass("java.lang.String"))
        assertThrows(ClassNotFoundException::class.java) { module.loadClass("no.such.Library") }
    }
}
