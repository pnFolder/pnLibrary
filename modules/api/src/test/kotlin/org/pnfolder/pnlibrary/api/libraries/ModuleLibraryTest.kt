package org.pnfolder.pnlibrary.api.libraries

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.platform.PlatformType

class ModuleLibraryTest {
    @Test
    fun `sources are tried in the given order and Maven Central last`() {
        val library = ModuleLibrary.builder("com.zaxxer", "HikariCP", "5.1.0")
            .source { repository("https://one.example/maven/") }.source { link("https://files.example/a.jar") }.build()

        assertEquals(listOf("https://one.example/maven/", "https://files.example/a.jar", ModuleLibrary.CENTRAL), library.allSources.map { it.url })
        assertEquals(listOf(false, true, false), library.allSources.map { it.direct })
        assertEquals("com.zaxxer:HikariCP:5.1.0", library.coordinates)
        assertEquals(LibraryScope.PLUGIN, library.scope)
        assertTrue(library.required)
        assertEquals(listOf(ModuleLibrary.CENTRAL), ModuleLibrary.of("g:a:1").allSources.map { it.url })
    }

    @Test
    fun `Maven Central can be turned off`() {
        val own = ModuleLibrary.builder("g", "a", "1").source { repository("https://one.example/") }.mavenCentral(false).build()
        assertEquals(listOf("https://one.example/"), own.allSources.map { it.url })
    }

    @Test
    fun `settings are checked when the library is built`() {
        assertThrows(IllegalArgumentException::class.java) { ModuleLibrary.builder("g", "a", "1").source { repository("http://plain.example/") } }
        assertThrows(IllegalArgumentException::class.java) { ModuleLibrary.builder("g", "a", "1").source { link("https://x.example/a.jar").sha256("abc") } }
        assertThrows(IllegalArgumentException::class.java) { ModuleLibrary.builder("g /x", "a", "1").build() }
        assertThrows(IllegalArgumentException::class.java) { ModuleLibrary.of("only:two") }
        assertThrows(IllegalStateException::class.java) { LibrarySource.builder().build() }
    }

    @Test
    fun `scope and optional change how the library is loaded`() {
        val library = ModuleLibrary.builder("g", "a", "1").scope(LibraryScope.ISOLATED).optional().build()

        assertTrue(library.isolated)
        assertFalse(library.required)
        assertEquals("g:a:1:shaded", ModuleLibrary.of("g:a:1:shaded").coordinates)
    }

    @Test
    fun `every source has an address, a sum and a name of its own`() {
        val one = "A".repeat(64)
        val library = ModuleLibrary.builder("g", "a", "1")
            .source { link("https://cdn.example/a.jar").sha256(one).name("CDN").description("  Быстрый  ") }
            .source { repository("https://mirror.example/") }.build()

        val all = library.allSources
        assertEquals("a".repeat(64), all[0].sha256)
        assertEquals("CDN", all[0].name)
        assertEquals("Быстрый", all[0].description)
        assertEquals(null, all[1].description)
        assertTrue(all[0].direct)
        assertEquals("mirror.example", all[1].name)
        assertEquals(null, all[1].sha256)
    }

    @Test
    fun `a library can be limited to some platforms`() {
        val any = ModuleLibrary.of("g:a:1")
        val bukkit = ModuleLibrary.builder("g", "a", "1").platforms(PlatformType.BUKKIT).build()

        assertTrue(any.appliesTo(PlatformType.VELOCITY))
        assertTrue(bukkit.appliesTo(PlatformType.BUKKIT))
        assertFalse(bukkit.appliesTo(PlatformType.VELOCITY))
        assertTrue(bukkit.appliesTo(null))
        assertThrows(IllegalArgumentException::class.java) { ModuleLibrary.builder("g", "a", "1").platforms(PlatformType.UNIVERSAL) }
    }

    @Test
    fun `a library has a readable title and description`() {
        val plain = ModuleLibrary.of("com.zaxxer:HikariCP:5.1.0")
        val named = ModuleLibrary.builder("com.zaxxer", "HikariCP", "5.1.0")
            .title("Пул соединений").description("  Держит открытые соединения с базой.  ").build()

        assertEquals("HikariCP", plain.title)
        assertEquals(null, plain.description)
        assertEquals("Пул соединений", named.title)
        assertEquals("Держит открытые соединения с базой.", named.description)
    }
}
