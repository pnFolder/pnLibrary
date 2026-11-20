package org.pnfolder.pnlibrary.loader;

import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Loads a library through Libby the first time something needs it: a database driver, the policy compiler. It is never
 * loaded at start, because most servers never need it.
 */
final class OptionalLoader {
    private final LibraryManager manager;
    private final LibrarySettings settings;
    private final Map<Object, LibraryManager> perPlugin = new IdentityHashMap<>();
    private volatile Function<Object, LibraryManager> managers;
    private final Map<String, Path> files = new ConcurrentHashMap<>();

    OptionalLoader(LibraryManager manager, LibrarySettings settings) {
        this.manager = manager;
        this.settings = settings;
    }

    /**
     * One attempt only: the caller reports a missing library itself, and an offline server must not wait.
     * Calls are serialized because Libby's manager is not safe for two threads loading at once.
     */
    synchronized boolean load(Library[] libraries) {
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

    /** Sets how a Libby manager for another plugin is made; without it a shared library joins pnLibrary's own loader. */
    void useManagers(Function<Object, LibraryManager> factory) {
        this.managers = factory;
    }

    /**
     * Loads one library a module asked for. The library names its own repositories; the repositories of the server's
     * settings are not added to it. An isolated library goes into a class loader of its own, named by the library's id,
     * and that loader is returned. A shared library joins the class loader of {@code plugin}, the plugin that asked, when
     * the platform can reach it; otherwise it joins pnLibrary's loader. {@code null} is returned for a shared library.
     *
     * @throws RuntimeException with the reason when the library cannot be downloaded or loaded
     */
    synchronized ClassLoader loadExternal(Library library, Object plugin) {
        LibraryManager target = library.isIsolatedLoad() ? manager : managerFor(plugin);
        target.loadLibrary(library);
        remember(target, library);
        return library.isIsolatedLoad() ? manager.getIsolatedClassLoaderOf(library.getId()) : null;
    }

    /** The jar a loaded library lives in, or {@code null} when it is unknown; the size and place shown to administrators. */
    Path fileOf(String coordinates) {
        return files.get(coordinates);
    }

    private void remember(LibraryManager target, Library library) {
        try {
            String key = library.getGroupId() + ":" + library.getArtifactId() + ":" + library.getVersion()
                    + (library.hasClassifier() ? ":" + library.getClassifier() : "");
            files.put(key, target.downloadLibrary(library));
        } catch (RuntimeException ignored) {
            // the place is only informative; the library is already loaded
        }
    }

    private LibraryManager managerFor(Object plugin) {
        Function<Object, LibraryManager> factory = managers;
        if (plugin == null || factory == null) {
            return manager;
        }
        LibraryManager own = perPlugin.get(plugin);
        if (own == null) {
            own = factory.apply(plugin);
            if (own == null) {
                return manager;
            }
            perPlugin.put(plugin, own);
        }
        return own;
    }
}
