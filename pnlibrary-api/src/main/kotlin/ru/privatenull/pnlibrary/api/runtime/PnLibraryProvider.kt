package ru.privatenull.pnlibrary.api.runtime

/** Process-wide access point installed by the pnLibrary platform plugin. */
object PnLibraryProvider {
    @Volatile private var instance: PnLibrary? = null

    @JvmStatic fun get(): PnLibrary = instance
        ?: throw IllegalStateException("pnLibrary runtime is not loaded; declare pnLibrary as a platform dependency")

    @JvmStatic fun getOrNull(): PnLibrary? = instance

    @JvmStatic @Synchronized fun install(library: PnLibrary) {
        check(instance == null || instance === library) { "A pnLibrary runtime is already installed" }
        instance = library
    }

    @JvmStatic @Synchronized fun clear(library: PnLibrary) {
        if (instance === library) instance = null
    }
}
