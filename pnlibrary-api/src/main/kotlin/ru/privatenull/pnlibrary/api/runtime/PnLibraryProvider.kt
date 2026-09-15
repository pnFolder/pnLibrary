package ru.privatenull.pnlibrary.api.runtime

/**
 * Process-wide access point installed by the pnLibrary platform plugin.
 *
 * Consumer plugins should declare pnLibrary as a platform dependency and call [get] only after
 * pnLibrary has loaded. Retaining the returned instance after platform shutdown is unsupported;
 * use [getOrNull] when code may run during bootstrap or teardown.
 */
object PnLibraryProvider {
    @Volatile
    private var instance: PnLibrary? = null

    /** Returns the installed runtime or fails outside its active lifecycle. */
    @JvmStatic
    fun get(): PnLibrary = instance
        ?: throw IllegalStateException("pnLibrary runtime is not loaded; declare pnLibrary as a platform dependency")

    /** Returns the installed runtime, or `null` outside its active lifecycle. */
    @JvmStatic
    fun getOrNull(): PnLibrary? = instance

    /** Installs [library]; replacing another live runtime is rejected. */
    @JvmStatic
    @Synchronized
    fun install(library: PnLibrary) {
        check(instance == null || instance === library) { "A pnLibrary runtime is already installed" }
        instance = library
    }

    /** Clears the provider only when [library] is the currently installed instance. */
    @JvmStatic
    @Synchronized
    fun clear(library: PnLibrary) {
        if (instance === library) {
            instance = null
        }
    }
}
