package org.pnfolder.pnlibrary.api.libraries

/**
 * The libraries of one module, or of a whole plugin (`PluginRegistration.libraries`).
 *
 * A module lists its libraries in `PluginModule.libraries()`; they are downloaded and verified before `onEnable` runs. A
 * plugin may register libraries on itself too: every module of the plugin sees them as [available]. Either may also ask
 * for one later with [load]. The library that fails to load stops the module (state `FAILED`) unless it was declared
 * optional.
 */
interface ModuleLibraries {
    /**
     * Class loader of the [LibraryScope.ISOLATED] libraries registered here; its classes are not visible to the plugin's own code, so load
     * their classes through this loader or find them with a `ServiceLoader` that uses it. A library declared
     * [LibraryScope.PLUGIN] is not here. [loadClass] is easier: it also looks in the plugin's libraries.
     */
    val classLoader: ClassLoader

    /** The libraries registered here, not counting the ones a plugin gives its modules. */
    val loaded: List<ModuleLibrary>

    /** The libraries this module can use: its own and the plugin's. For a plugin it is the same as [loaded]. */
    val available: List<ModuleLibrary> get() = loaded

    /** Every library registered here with where it stands, also the switched off and the failed ones; [loaded] lists only the loaded. */
    val statuses: List<LibraryStatus>

    /** Where [library] stands, or `null` when it was never registered here. */
    fun status(library: ModuleLibrary): LibraryStatus?

    /**
     * Chooses whether [library] is loaded with the server, and saves the choice. Switching it on loads it now; switching
     * it off keeps it from loading again, and a library already in use stays until the server restarts
     * ([LibraryStatus.restartRequired]).
     */
    fun setAutoLoad(library: ModuleLibrary, enabled: Boolean): LibraryStatus

    /** Tries to load a failed library again; a switched off or loaded one is left as it is. Never throws: the reason is in the status. */
    fun retry(library: ModuleLibrary): LibraryStatus

    /** Downloads, verifies and loads [library] unless it is loaded already. Throws [IllegalStateException] with the reason when it fails. */
    fun load(library: ModuleLibrary)

    /** Loads a library given as `group:artifact:version` with default settings. */
    fun load(coordinates: String) = load(ModuleLibrary.of(coordinates))

    /** Whether [library] is loaded here or, for a module, by its plugin. */
    fun isLoaded(library: ModuleLibrary): Boolean = library in available

    /** The available library with this [group] and [artifact], whatever its version, or `null`. */
    fun find(group: String, artifact: String): ModuleLibrary? = available.firstOrNull { it.group == group && it.artifact == artifact }

    /**
     * Finds the class [name] in the libraries registered here, then, for a module, in the plugin's. Throws
     * [ClassNotFoundException] when no library has it.
     */
    fun loadClass(name: String): Class<*>

    /**
     * Takes [library] off the list, so it is not shown any more and is not loaded again when a module starts. Java cannot
     * unload a jar that is already in use: its classes stay available until the server restarts. Returns whether it was
     * on the list.
     */
    fun remove(library: ModuleLibrary): Boolean
}
