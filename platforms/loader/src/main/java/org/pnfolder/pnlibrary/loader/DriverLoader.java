package org.pnfolder.pnlibrary.loader;

import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;

/**
 * Loads a database driver through Libby the first time a backup needs it. A driver is never loaded at start:
 * most servers keep no database that pnLibrary backs up.
 */
final class DriverLoader {
    private final LibraryManager manager;
    private final LibrarySettings settings;

    DriverLoader(LibraryManager manager, LibrarySettings settings) {
        this.manager = manager;
        this.settings = settings;
    }

    /** One attempt only: the backup reports a missing driver itself, and an offline server must not wait. */
    boolean load(Library[] driver) {
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
            for (Library library : driver) {
                manager.loadLibrary(library);
            }
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
