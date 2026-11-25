package org.pnfolder.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings
import org.mockito.Mockito.`when`
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry
import org.pnfolder.pnlibrary.api.runtime.PnLibrary

class PnLibraryGroupsTest {
    private val library = mock(PnLibrary::class.java, withSettings().defaultAnswer(CALLS_REAL_METHODS))
    private val logging = mock(LoggingService::class.java)
    private val diagnostics = mock(DiagnosticsService::class.java)
    private val plugins = mock(PluginRegistry::class.java)

    init {
        `when`(library.logging).thenReturn(logging)
        `when`(library.diagnostics).thenReturn(diagnostics)
        `when`(library.plugins).thenReturn(plugins)
        `when`(library.version).thenReturn("1.2.3")
        `when`(library.isClosed).thenReturn(true)
    }

    @Test
    fun `observe gives the same objects as the flat fields`() {
        assertSame(logging, library.observe.logging)
        assertSame(diagnostics, library.observe.diagnostics)
    }

    @Test
    fun `registry gives the same plugin registry`() {
        assertSame(plugins, library.registry.plugins)
    }

    @Test
    fun `runtime reads version and closed state from the library`() {
        assertEquals("1.2.3", library.runtime.version)
        assertEquals(true, library.runtime.isClosed)
    }
}
