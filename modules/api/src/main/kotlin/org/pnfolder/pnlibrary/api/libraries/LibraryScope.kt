package org.pnfolder.pnlibrary.api.libraries

/** Where the classes of a [ModuleLibrary] become visible. */
enum class LibraryScope {
    /**
     * The class loader of the plugin that owns the module: its own code sees the library directly. The default. On a
     * platform that cannot add to a plugin's loader (Velocity) the library joins pnLibrary's loader instead.
     */
    PLUGIN,

    /**
     * A class loader of its own for this module ([ModuleLibraries.classLoader]). Two modules can use two versions of the
     * same library without clashing; the price is that the plugin's own code reaches it only through [ModuleLibraries.loadClass].
     */
    ISOLATED,

    /**
     * pnLibrary's own class loader, visible to every plugin that depends on pnLibrary. Rarely wanted: a second version of the
     * same library from another plugin clashes. Use it only for a library that plugins must share on purpose.
     */
    LIBRARY,
}
