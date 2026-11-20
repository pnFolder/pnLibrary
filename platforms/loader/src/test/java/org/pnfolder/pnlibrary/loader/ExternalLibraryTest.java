package org.pnfolder.pnlibrary.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import net.byteflux.libby.classloader.IsolatedClassLoader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Checks that a library a module asks for keeps its repositories, checksum and class loader. */
class ExternalLibraryTest {
    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static LibraryManager start() {
        LibraryManager manager = mock(LibraryManager.class);
        LibraryLoader.load(manager, ExternalLibraryTest.class.getClassLoader(), LibrarySettings.builder().build());
        return manager;
    }

    @Test
    void passesTheModulesRepositoriesAndChecksumToLibby() {
        LibraryManager manager = start();
        IsolatedClassLoader holder = new IsolatedClassLoader();
        when(manager.getIsolatedClassLoaderOf("shop")).thenReturn(holder);

        ClassLoader result = LibraryLoader.loadExternal("shop", "com.zaxxer", "HikariCP", "5.1.0", null, SHA, null,
                new String[] {"https://one.example/", "https://repo1.maven.org/maven2/"}, true, null);

        ArgumentCaptor<Library> loaded = ArgumentCaptor.forClass(Library.class);
        verify(manager).loadLibrary(loaded.capture());
        assertSame(holder, result);
        assertEquals("shop", loaded.getValue().getId());
        assertTrue(loaded.getValue().isIsolatedLoad());
        assertTrue(loaded.getValue().hasChecksum());
        assertEquals(2, loaded.getValue().getRepositories().size());
    }

    @Test
    void aSharedLibraryJoinsTheLoaderOfPnLibrary() {
        start();

        assertEquals(null, LibraryLoader.loadExternal("shop", "g", "a", "1", null, null, null, new String[0], false, null));
    }

    @Test
    void aFailureCarriesTheReason() {
        LibraryManager manager = start();
        doThrow(new RuntimeException("404")).when(manager).loadLibrary(any(Library.class));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> LibraryLoader.loadExternal("shop", "g", "a", "1", null, null, null, new String[0], true, null));
        assertTrue(failure.getMessage().contains("g:a:1"));
        assertTrue(failure.getMessage().contains("404"));
    }
}
