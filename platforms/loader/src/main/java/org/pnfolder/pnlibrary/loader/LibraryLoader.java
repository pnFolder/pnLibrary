package org.pnfolder.pnlibrary.loader;

import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;

/**
 * Loads the libraries a light pnLibrary JAR does not pack, through Libby.
 *
 * <p>Libby downloads each file, checks its SHA-256 and adds it to the plugin classloader. Where it downloads from
 * is set by {@link LibrarySettings}; what it downloads is listed in {@link RuntimeLibraries}. The full JARs already
 * hold Kotlin, so nothing is downloaded for them.
 *
 * <p>Only Kotlin is loaded at start. Everything else is optional and loaded by {@link #loadOptional} the moment
 * something needs it, so a server that never uses a database backup or a remote policy downloads nothing for them.
 */
public final class LibraryLoader {
    private static final String PROBE = "kotlin.Unit";
    // 3.26 is the last ECJ that runs on Java 8; the newer one needs Java 17.
    private static final int MODERN_COMPILER_JAVA = 17;

    // Libby is tied to the plugin that started it, so later loads have to reuse that one manager.
    private static volatile OptionalLoader optional;

    private LibraryLoader() {
    }

    /**
     * Loads {@code libraries} as {@code settings} say, unless Kotlin is already visible to {@code loader}.
     * @throws LibraryLoadException if a library cannot be loaded after every attempt
     */
    public static void load(LibraryManager manager, ClassLoader loader, LibrarySettings settings, Library... libraries) {
        optional = new OptionalLoader(manager, settings);
        if (present(loader, PROBE)) {
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

    /**
     * Loads an optional library group the first time it is needed: {@code mysql}, {@code postgres}, {@code mongo} or {@code compiler}.
     * Returns false when it cannot be loaded, for example offline; the caller says what is missing. Called by name
     * from the runtime, which cannot see this module.
     */
    public static boolean loadOptional(String id) {
        OptionalLoader loader = optional;
        Library[] libraries = librariesFor(id, javaFeature());
        return loader != null && libraries != null && loader.load(libraries);
    }

    /** The libraries of an optional group, or {@code null} for an unknown id; {@code javaFeature} picks the compiler. */
    static Library[] librariesFor(String id, int javaFeature) {
        switch (id) {
            case "mysql":
                return RuntimeLibraries.DRIVER_MYSQL;
            case "postgres":
                return RuntimeLibraries.DRIVER_POSTGRES;
            case "mongo":
                return RuntimeLibraries.DRIVER_MONGO;
            case "compiler":
                return javaFeature >= MODERN_COMPILER_JAVA ? RuntimeLibraries.COMPILER_MODERN : RuntimeLibraries.COMPILER_LEGACY;
            default:
                return null;
        }
    }

    /** The running Java feature version (8, 11, 17, ...); 8 when it cannot be read. */
    static int javaFeature() {
        String version = System.getProperty("java.specification.version", "8");
        String feature = version.startsWith("1.") ? version.substring(2) : version;
        try {
            return Math.max(8, Integer.parseInt(feature.split("[.-]")[0]));
        } catch (NumberFormatException unknown) {
            return 8;
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

    private static boolean present(ClassLoader loader, String name) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }
}
