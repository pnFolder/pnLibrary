package org.pnfolder.pnlibrary.loader;

import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;

/**
 * Loads a library through Libby the first time something needs it: a database driver, the policy compiler. It is never
 * loaded at start,
 * because most servers never need it.
 */
final class OptionalLoader {
    private final LibraryManager manager;
    private final LibrarySettings settings;

    OptionalLoader(LibraryManager manager, LibrarySettings settings) {
        this.manager = manager;
        this.settings = settings;
    }

    /** One attempt only: the caller reports a missing library itself, and an offline server must not wait. */
    boolean load(Library[] libraries) {
        for (String repository : settings.repositories()) {
            manager.addRepository(repository);
        }
        if (settings.mavenCentral()) {
            manager.addMavenCentral();
        }
        for (String fallback : settings.fallbacks()) {
            manager.addRepository(fallback);
        }
        try {
            for (Library library : libraries) {
                manager.loadLibrary(library);
            }
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
