package org.pnfolder.pnlibrary.core.plugin.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.libraries.LibraryScope;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;

class ModuleLibrariesTest {
    private final ModuleLibrary hikari = ModuleLibrary.of("com.zaxxer:HikariCP:5.1.0");

    @Test
    void aLibraryIsFetchedOnceAndItsIsolatedLoaderIsKept() {
        URLClassLoader holder = new URLClassLoader(new URL[0]);
        AtomicInteger fetched = new AtomicInteger();
        ModuleLibraryLoader libraries = new ModuleLibraryLoader(null, library -> {
            fetched.incrementAndGet();
            return holder;
        });

        libraries.load(hikari);
        libraries.load(hikari);

        assertEquals(1, fetched.get());
        assertSame(holder, libraries.getClassLoader());
        assertEquals(Collections.singletonList(hikari), libraries.getLoaded());
    }

    @Test
    void aSharedLibraryAddsNoLoaderAndAFailureIsNotRememberedAsLoaded() {
        ModuleLibrary shared = ModuleLibrary.builder("g", "a", "1").scope(LibraryScope.LIBRARY).build();
        ModuleLibraryLoader libraries = new ModuleLibraryLoader(null, library -> {
            if (library.equals(hikari)) {
                throw new IllegalStateException("offline");
            }
            return null;
        });

        libraries.load(shared);
        assertThrows(IllegalStateException.class, () -> libraries.load(hikari));
        assertEquals(Collections.singletonList(shared), libraries.getLoaded());
    }

    @Test
    void aModuleSeesTheLibrariesOfItsPluginAndDoesNotFetchThemTwice() {
        AtomicInteger fetched = new AtomicInteger();
        ModuleLibraryLoader plugin = new ModuleLibraryLoader(null, library -> {
            fetched.incrementAndGet();
            return null;
        });
        ModuleLibraryLoader module = new ModuleLibraryLoader(plugin, library -> {
            fetched.incrementAndGet();
            return null;
        });
        ModuleLibrary own = ModuleLibrary.of("g:own:1");

        plugin.load(hikari);
        module.load(hikari);
        module.load(own);

        assertEquals(2, fetched.get());
        assertEquals(Collections.singletonList(own), module.getLoaded());
        assertEquals(Arrays.asList(hikari, own), module.getAvailable());
        assertEquals(Collections.singletonList(hikari), plugin.getAvailable());
        assertEquals(hikari, module.find("com.zaxxer", "HikariCP"));
    }

    @Test
    void removeTakesALibraryOffTheListAndTellsWhetherItWasThere() {
        ModuleLibraryLoader libraries = new ModuleLibraryLoader(null, library -> null);
        libraries.load(hikari);

        assertEquals(true, libraries.remove(hikari));
        assertEquals(false, libraries.remove(hikari));
        assertEquals(Collections.<ModuleLibrary>emptyList(), libraries.getLoaded());
    }

    @Test
    void aClassIsSearchedInTheModuleLoaderFirstAndThenInThePluginLoader() throws Exception {
        ModuleLibraryLoader plugin = new ModuleLibraryLoader(null, library -> null);
        ModuleLibraryLoader module = new ModuleLibraryLoader(plugin, library -> null);

        assertSame(String.class, module.loadClass("java.lang.String"));
        assertThrows(ClassNotFoundException.class, () -> module.loadClass("no.such.Library"));
    }
}
