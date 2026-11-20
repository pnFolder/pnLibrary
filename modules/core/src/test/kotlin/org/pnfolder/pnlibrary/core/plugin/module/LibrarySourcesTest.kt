package org.pnfolder.pnlibrary.core.plugin.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.api.platform.PlatformType

class LibrarySourcesTest {
    @Test
    fun `a library for another platform is skipped without an error`() {
        var fetched = 0
        val libraries = ModuleLibrariesImpl(null, PlatformType.VELOCITY) { fetched++; null }
        libraries.load(ModuleLibrary.builder("g", "a", "1").platforms(PlatformType.BUKKIT).build())
        libraries.load(ModuleLibrary.builder("g", "b", "1").platforms(PlatformType.VELOCITY, PlatformType.BUNGEECORD).build())

        assertEquals(1, fetched)
        assertEquals(listOf("g:b:1"), libraries.loaded.map { it.coordinates })
    }

    @Test
    fun `any number of sources are tried in the given order`() {
        val library = ModuleLibrary.builder("g", "a", "1")
            .source { link("https://one.example/a.jar") }.source { link("https://two.example/a.jar") }
            .source { repository("https://repo1.example/") }.source { repository("https://repo2.example/") }.mavenCentral(false).build()
        assertEquals(
            listOf("https://one.example/a.jar", "https://two.example/a.jar", "https://repo1.example/", "https://repo2.example/"),
            library.allSources.map { it.url },
        )
    }

    @Test
    fun `a thousand alternatives are accepted`() {
        val builder = ModuleLibrary.builder("g", "a", "1").mavenCentral(false)
        repeat(1000) { index -> builder.source { repository("https://m$index.example/") } }
        assertEquals(1000, builder.build().allSources.size)
    }

    @Test
    fun `a place that failed a moment ago is tried last and forgotten after five minutes`() {
        SourceHealth.forgetAll()
        val library = ModuleLibrary.builder("g", "a", "1")
            .source { repository("https://one.example/") }.source { repository("https://two.example/") }.mavenCentral(false).build()
        SourceHealth.failed("https://one.example/", now = 1_000)

        assertEquals(listOf("https://two.example/", "https://one.example/"), SourceHealth.order(library.allSources, now = 2_000).map { it.url })
        assertEquals(listOf("https://one.example/", "https://two.example/"), SourceHealth.order(library.allSources, now = 400_000).map { it.url })
        SourceHealth.forgetAll()
    }
}
