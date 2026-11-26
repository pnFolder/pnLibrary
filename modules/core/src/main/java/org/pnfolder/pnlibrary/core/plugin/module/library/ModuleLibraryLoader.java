package org.pnfolder.pnlibrary.core.plugin.module.library;

import org.pnfolder.pnlibrary.core.plugin.module.ModulePreferences;
import java.util.Objects;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibraries;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryState;
import org.pnfolder.pnlibrary.api.libraries.state.LibraryStatus;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

/**
 * The libraries of one module or plugin. The fetcher downloads one library and returns its isolated class loader, or
 * {@code null} for a shared one; in the runtime it asks the Libby loader, in tests it is replaced. A module has its plugin's
 * libraries as parent. An administrator's choice to keep a library off is saved in the preferences under the scope.
 */
public final class ModuleLibraryLoader implements ModuleLibraries {
    private static final class Entry {
        private LibraryState state;
        private String error;
        private boolean restartRequired;

        private Entry(LibraryState state, String error) {
            this.state = state;
            this.error = error;
        }
    }

    private final ModuleLibraries parent;
    private final PlatformType platform;
    private final ModulePreferences preferences;
    private final String scope;
    private final Function<ModuleLibrary, ClassLoader> fetch;
    private final Set<ModuleLibrary> libraries = new LinkedHashSet<>();
    private final Map<ModuleLibrary, Entry> entries = new LinkedHashMap<>();
    private ClassLoader isolated;
    private ClassLoader empty;

    public ModuleLibraryLoader(@Nullable ModuleLibraries parent, @Nullable PlatformType platform, @NotNull ModulePreferences preferences,
                               @NotNull String scope, @NotNull Function<ModuleLibrary, ClassLoader> fetch) {
        this.parent = parent;
        this.platform = platform;
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.fetch = Objects.requireNonNull(fetch, "fetch");
    }

    public ModuleLibraryLoader(@Nullable ModuleLibraries parent, @Nullable PlatformType platform,
                               @NotNull Function<ModuleLibrary, ClassLoader> fetch) {
        this(parent, platform, new ModulePreferences(), "", fetch);
    }

    public ModuleLibraryLoader(@Nullable ModuleLibraries parent, @NotNull Function<ModuleLibrary, ClassLoader> fetch) {
        this(parent, null, fetch);
    }

    /** Libraries that {@code fetcher} downloads on behalf of {@code owner} and {@code plugin}; the class loader belongs to the runtime. */
    public static @NotNull ModuleLibraryLoader through(@NotNull final String owner, @NotNull final ClassLoader classLoader,
                                                       @Nullable final Object plugin, @Nullable ModuleLibraries parent,
                                                       @NotNull final LibraryFetcher fetcher, @Nullable PlatformType platform,
                                                       @NotNull ModulePreferences preferences, @NotNull String scope) {
        return new ModuleLibraryLoader(parent, platform, preferences, scope, library -> fetcher.fetch(classLoader, owner, plugin, library));
    }

    public static @NotNull ModuleLibraryLoader through(@NotNull String owner, @NotNull ClassLoader classLoader) {
        return through(owner, classLoader, null, null, LibraryFetcher.THROUGH_LIBBY, null, new ModulePreferences(), owner);
    }

    @Override
    public synchronized @NotNull ClassLoader getClassLoader() {
        if (isolated != null) {
            return isolated;
        }
        if (empty == null) {
            empty = new URLClassLoader(new URL[0], null);
        }
        return empty;
    }

    @Override
    public synchronized @NotNull List<ModuleLibrary> getLoaded() {
        return new ArrayList<>(libraries);
    }

    @Override
    public @NotNull List<ModuleLibrary> getAvailable() {
        List<ModuleLibrary> all = new ArrayList<>();
        if (parent != null) {
            all.addAll(parent.getAvailable());
        }
        all.addAll(getLoaded());
        return new ArrayList<>(new LinkedHashSet<>(all));
    }

    @Override
    public synchronized @NotNull List<LibraryStatus> getStatuses() {
        List<LibraryStatus> statuses = new ArrayList<>();
        for (ModuleLibrary library : entries.keySet()) {
            statuses.add(statusOf(library));
        }
        return statuses;
    }

    private String key(ModuleLibrary library) {
        return "lib|" + scope + "|" + library.getCoordinates();
    }

    private LibraryStatus statusOf(ModuleLibrary library) {
        Entry entry = entries.get(library);
        return new LibraryStatus(library, entry.state, preferences.autoStart(key(library)), entry.error, entry.restartRequired);
    }

    @Override
    public synchronized @Nullable LibraryStatus status(@NotNull ModuleLibrary library) {
        return entries.containsKey(library) ? statusOf(library) : null;
    }

    @Override
    public synchronized void load(@NotNull ModuleLibrary library) {
        if (!library.appliesTo(platform)) {
            if (!entries.containsKey(library)) {
                entries.put(library, new Entry(LibraryState.SKIPPED, null));
            }
            return;
        }
        if (libraries.contains(library) || (parent != null && parent.isLoaded(library))) {
            return;
        }
        if (!preferences.autoStart(key(library))) {
            entries.put(library, new Entry(LibraryState.DISABLED, null));
            return;
        }
        attempt(library);
    }

    /** Downloads and loads the library; a failure is kept in its entry and thrown. */
    private void attempt(ModuleLibrary library) {
        try {
            ClassLoader holder = fetch.apply(library);
            if (holder != null) {
                isolated = holder;
            }
            libraries.add(library);
            entries.put(library, new Entry(LibraryState.LOADED, null));
        } catch (RuntimeException failure) {
            String reason = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
            entries.put(library, new Entry(LibraryState.FAILED, reason));
            throw failure;
        }
    }

    private void attemptQuietly(ModuleLibrary library) {
        try {
            attempt(library);
        } catch (RuntimeException ignored) {
            // the failure is kept in the entry and shown as the library's status
        }
    }

    @Override
    public synchronized @NotNull LibraryStatus setAutoLoad(@NotNull ModuleLibrary library, boolean enabled) {
        preferences.setAutoStart(key(library), enabled);
        Entry entry = entries.get(library);
        if (entry == null) {
            return new LibraryStatus(library, LibraryState.DISABLED, enabled);
        }
        if (enabled) {
            entry.restartRequired = false;
            if (entry.state == LibraryState.DISABLED || entry.state == LibraryState.FAILED) {
                attemptQuietly(library);
            }
        } else if (entry.state == LibraryState.LOADED) {
            entry.restartRequired = true;
        } else if (entry.state == LibraryState.FAILED) {
            entry.state = LibraryState.DISABLED;
            entry.error = null;
        }
        return statusOf(library);
    }

    @Override
    public synchronized @NotNull LibraryStatus retry(@NotNull ModuleLibrary library) {
        Entry entry = entries.get(library);
        if (entry == null) {
            return new LibraryStatus(library, LibraryState.DISABLED, preferences.autoStart(key(library)));
        }
        if (entry.state == LibraryState.FAILED && preferences.autoStart(key(library))) {
            attemptQuietly(library);
        }
        return statusOf(library);
    }

    @Override
    public @NotNull Class<?> loadClass(@NotNull String name) throws ClassNotFoundException {
        ClassLoader own;
        synchronized (this) {
            own = isolated;
        }
        if (own != null) {
            try {
                return own.loadClass(name);
            } catch (ClassNotFoundException notHere) {
                // not here: the plugin's libraries, then the shared ones
            }
        }
        return parent != null ? parent.loadClass(name) : getClass().getClassLoader().loadClass(name);
    }

    @Override
    public synchronized boolean remove(@NotNull ModuleLibrary library) {
        entries.remove(library);
        return libraries.remove(library);
    }
}
