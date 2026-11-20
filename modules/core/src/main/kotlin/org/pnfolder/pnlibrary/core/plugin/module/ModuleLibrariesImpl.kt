package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.libraries.ModuleLibraries
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.net.URL
import java.net.URLClassLoader

/**
 * The libraries of one module or plugin. [fetch] downloads one library and returns its isolated class loader, or `null`
 * for a shared one; in the runtime it asks the Libby loader, in tests it is replaced. A module has its plugin's libraries
 * as [parent].
 */
internal class ModuleLibrariesImpl(
    private val parent: ModuleLibraries? = null,
    private val platform: PlatformType? = null,
    private val fetch: (ModuleLibrary) -> ClassLoader?,
) : ModuleLibraries {
    private val libraries = LinkedHashSet<ModuleLibrary>()
    private var isolated: ClassLoader? = null
    private val empty by lazy { URLClassLoader(emptyArray<URL>(), null) }

    override val classLoader: ClassLoader @Synchronized get() = isolated ?: empty

    override val loaded: List<ModuleLibrary> @Synchronized get() = libraries.toList()

    override val available: List<ModuleLibrary> get() = ((parent?.available ?: emptyList()) + loaded).distinct()

    @Synchronized
    override fun load(library: ModuleLibrary) {
        if (!library.appliesTo(platform)) return
        if (library in libraries || parent?.isLoaded(library) == true) return
        val holder = fetch(library)
        if (holder != null) isolated = holder
        libraries += library
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
    override fun remove(library: ModuleLibrary): Boolean = libraries.remove(library)

    companion object {
        /** Libraries that [fetcher] downloads on behalf of [owner] and [plugin]; [classLoader] belongs to the runtime. */
        fun through(
            owner: String,
            classLoader: ClassLoader,
            plugin: Any? = null,
            parent: ModuleLibraries? = null,
            fetcher: LibraryFetcher = LibraryFetcher.THROUGH_LIBBY,
            platform: PlatformType? = null,
        ) = ModuleLibrariesImpl(parent, platform) { library -> fetcher.fetch(classLoader, owner, plugin, library) }
    }
}
