package org.pnfolder.pnlibrary.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;

class PnLibraryGroupsTest {
    private final PnLibrary library = mock(PnLibrary.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
    private final LoggingService logging = mock(LoggingService.class);
    private final DiagnosticsService diagnostics = mock(DiagnosticsService.class);
    private final PluginRegistry plugins = mock(PluginRegistry.class);

    PnLibraryGroupsTest() {
        when(library.getLogging()).thenReturn(logging);
        when(library.getDiagnostics()).thenReturn(diagnostics);
        when(library.getPlugins()).thenReturn(plugins);
        when(library.getVersion()).thenReturn("1.2.3");
        when(library.isClosed()).thenReturn(true);
    }

    @Test
    void observeGivesTheSameObjectsAsTheFlatFields() {
        assertSame(logging, library.getObserve().getLogging());
        assertSame(diagnostics, library.getObserve().getDiagnostics());
    }

    @Test
    void registryGivesTheSamePluginRegistry() {
        assertSame(plugins, library.getRegistry().getPlugins());
    }

    @Test
    void runtimeReadsVersionAndClosedStateFromTheLibrary() {
        assertEquals("1.2.3", library.getRuntime().getVersion());
        assertEquals(true, library.getRuntime().isClosed());
    }
}
