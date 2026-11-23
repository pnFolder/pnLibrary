package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.libraries.LibraryState
import org.pnfolder.pnlibrary.api.libraries.LibraryStatus
import org.pnfolder.pnlibrary.api.libraries.ModuleLibraries
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.net.URL
import java.net.URLClassLoader

/**
 * The libraries of one module or plugin. [fetch] downloads one library and returns its isolated class loader, or `null`
 * for a shared one; in the runtime it asks the Libby loader, in tests it is replaced. A module has its plugin's libraries
 * as [parent]. An administrator's choice to keep a library off is saved in [preferences] under [scope].
 */
internal class ModuleLibrariesImpl(
    private val parent: ModuleLibraries? = null,
    private val platform: PlatformType? = null,
    private val preferences: ModulePreferences = ModulePreferences(),
    private val scope: String = "",
    private val fetch: (ModuleLibrary) -> ClassLoader?,
) : ModuleLibraries {
    private class Entry(var state: LibraryState, var error: String? = null, var restartRequired: Boolean = false)

    private val libraries = LinkedHashSet<ModuleLibrary>()
    private val entries = LinkedHashMap<ModuleLibrary, Entry>()
    private var isolated: ClassLoader? = null
    private val empty by lazy { URLClassLoader(emptyArray<URL>(), null) }

    override val classLoader: ClassLoader @Synchronized get() = isolated ?: empty

    override val loaded: List<ModuleLibrary> @Synchronized get() = libraries.toList()

    override val available: List<ModuleLibrary> get() = ((parent?.available ?: emptyList()) + loaded).distinct()

    override val statuses: List<LibraryStatus> @Synchronized get() = entries.keys.map { statusOf(it) }

    private fun key(library: ModuleLibrary) = "lib|$scope|${library.coordinates}"

    @Synchronized
    private fun statusOf(library: ModuleLibrary): LibraryStatus {
        val entry = entries.getValue(library)
        return LibraryStatus(library, entry.state, preferences.autoStart(key(library)), entry.error, entry.restartRequired)
    }

    @Synchronized
    override fun status(library: ModuleLibrary): LibraryStatus? = if (library in entries) statusOf(library) else null

    @Synchronized
    override fun load(library: ModuleLibrary) {
        if (!library.appliesTo(platform)) {
            entries.getOrPut(library) { Entry(LibraryState.SKIPPED) }
            return
        }
        if (library in libraries || parent?.isLoaded(library) == true) return
        if (!preferences.autoStart(key(library))) {
            entries[library] = Entry(LibraryState.DISABLED)
            return
        }
        attempt(library)
    }

    /** Downloads and loads [library]; a failure is kept in its entry and thrown. */
    private fun attempt(library: ModuleLibrary) {
        try {
            val holder = fetch(library)
            if (holder != null) isolated = holder
            libraries += library
            entries[library] = Entry(LibraryState.LOADED)
        } catch (failure: Exception) {
            entries[library] = Entry(LibraryState.FAILED, failure.message ?: failure.javaClass.simpleName)
            throw failure
        }
    }

    @Synchronized
    override fun setAutoLoad(library: ModuleLibrary, enabled: Boolean): LibraryStatus {
        preferences.setAutoStart(key(library), enabled)
        val entry = entries[library] ?: return LibraryStatus(library, LibraryState.DISABLED, enabled)
        if (enabled) {
            entry.restartRequired = false
            if (entry.state == LibraryState.DISABLED || entry.state == LibraryState.FAILED) {
                try { attempt(library) } catch (_: Exception) { }
            }
        } else if (entry.state == LibraryState.LOADED) {
            entry.restartRequired = true
        } else if (entry.state == LibraryState.FAILED) {
            entry.state = LibraryState.DISABLED
            entry.error = null
        }
        return statusOf(library)
    }

    @Synchronized
    override fun retry(library: ModuleLibrary): LibraryStatus {
        val entry = entries[library] ?: return LibraryStatus(library, LibraryState.DISABLED, preferences.autoStart(key(library)))
        if (entry.state == LibraryState.FAILED && preferences.autoStart(key(library))) {
            try { attempt(library) } catch (_: Exception) { }
        }
        return statusOf(library)
    }

    override fun loadClass(name: String): Class<*> {
        val own = synchronized(this) { isolated }
        if (own != null) {
            try {
                return own.loadClass(name)
            } catch (_: ClassNotFoundException) {
                // not here: the plugin's libraries, then the shared ones
            }
        }
        return parent?.loadClass(name) ?: javaClass.classLoader.loadClass(name)
    }

    @Synchronized
    override fun remove(library: ModuleLibrary): Boolean {
        entries.remove(library)
        return libraries.remove(library)
    }

    companion object {
        /** Libraries that [fetcher] downloads on behalf of [owner] and [plugin]; [classLoader] belongs to the runtime. */
        fun through(
            owner: String,
            classLoader: ClassLoader,
            plugin: Any? = null,
            parent: ModuleLibraries? = null,
            fetcher: LibraryFetcher = LibraryFetcher.THROUGH_LIBBY,
            platform: PlatformType? = null,
            preferences: ModulePreferences = ModulePreferences(),
            scope: String = owner,
        ) = ModuleLibrariesImpl(parent, platform, preferences, scope) { library -> fetcher.fetch(classLoader, owner, plugin, library) }
    }
}
