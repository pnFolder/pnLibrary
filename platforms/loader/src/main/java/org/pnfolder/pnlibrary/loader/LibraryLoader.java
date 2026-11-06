package org.pnfolder.pnlibrary.loader;

import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;

import javax.tools.ToolProvider;

/**
 * Loads the libraries a light pnLibrary JAR does not pack, through Libby.
 *
 * <p>Libby downloads each file, checks its SHA-256 and adds it to the plugin classloader. Where it downloads from
 * is set by {@link LibrarySettings}; what it downloads is listed in {@link RuntimeLibraries}. The full JARs already
 * hold Kotlin, so nothing is downloaded for them.
 */
public final class LibraryLoader {
    private static final String PROBE = "kotlin.Unit";
    private static final String COMPILER_PROBE = "org.eclipse.jdt.internal.compiler.tool.EclipseCompiler";
    // 3.26 is the last ECJ that runs on Java 8; the newer one needs Java 17.
    private static final int MODERN_COMPILER_JAVA = 17;

    private LibraryLoader() {
    }

    /**
     * Loads {@code libraries} as {@code settings} say, unless Kotlin is already visible to {@code loader}. On a server
     * that runs a JRE without javac it also loads the ECJ compiler, which remote policies are compiled with.
     * @throws LibraryLoadException if a library cannot be loaded after every attempt
     */
    public static void load(LibraryManager manager, ClassLoader loader, LibrarySettings settings, Library... libraries) {
        boolean kotlinMissing = !present(loader, PROBE);
        boolean compilerMissing = needsCompiler(loader);
        if (!kotlinMissing && !compilerMissing) {
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
        if (kotlinMissing) {
            for (Library library : libraries) {
                loadWithRetry(manager, library, settings.attempts());
            }
        }
        if (compilerMissing) {
            loadCompiler(manager, javaFeature());
        }
    }

    /**
     * A policy can still be checked without the compiler, so a failure here must not stop the server: the policy
     * check reports the missing compiler itself and {@code onError} decides. One attempt only, so an offline
     * server does not wait on every start.
     */
    static void loadCompiler(LibraryManager manager, int feature) {
        Library[] compiler = feature >= MODERN_COMPILER_JAVA ? RuntimeLibraries.COMPILER_MODERN : RuntimeLibraries.COMPILER_LEGACY;
        for (Library library : compiler) {
            try {
                manager.loadLibrary(library);
            } catch (RuntimeException failure) {
                return;
            }
        }
    }

    private static boolean needsCompiler(ClassLoader loader) {
        return ToolProvider.getSystemJavaCompiler() == null && !present(loader, COMPILER_PROBE);
    }

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
