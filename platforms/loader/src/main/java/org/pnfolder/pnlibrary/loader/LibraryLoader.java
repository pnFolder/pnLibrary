package org.pnfolder.pnlibrary.loader;

import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;

/**
 * Loads the libraries a light pnLibrary JAR does not pack, through Libby.
 *
 * <p>Libby downloads each file, checks its SHA-256 and adds it to the plugin classloader. Where it downloads from
 * is set by {@link LibrarySettings}; what it downloads is listed in {@link RuntimeLibraries}. The full JARs already
 * hold Kotlin, so nothing is downloaded for them.
 */
public final class LibraryLoader {
    private static final String PROBE = "kotlin.Unit";

    private LibraryLoader() {
    }

    /**
     * Loads {@code libraries} as {@code settings} say, unless Kotlin is already visible to {@code loader}.
     *
     * @param manager Libby manager that downloads and attaches the JARs
     * @param loader class loader the libraries must become visible to
     * @param settings folder, repositories and retry policy
     * @param libraries libraries to load, in order
     * @throws LibraryLoadException if a library cannot be loaded after every attempt
     */
    public static void load(LibraryManager manager, ClassLoader loader, LibrarySettings settings, Library... libraries) {
        if (present(loader)) {
            return;
        }
        for (String repository : settings.repositories()) {
            manager.addRepository(repository);
        }
        if (settings.mavenCentral()) {
            manager.addMavenCentral();
        }
        for (String fallback : settings.fallbacks()) {
            manager.addRepository(fallback);
        }
        for (Library library : libraries) {
            loadWithRetry(manager, library, settings.attempts());
        }
    }

    private static void loadWithRetry(LibraryManager manager, Library library, int attempts) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                manager.loadLibrary(library);
                return;
            } catch (RuntimeException failure) {
                last = failure;
            }
        }
        throw new LibraryLoadException(library, attempts, last);
    }

    private static boolean present(ClassLoader loader) {
        try {
            Class.forName(PROBE, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }
}
