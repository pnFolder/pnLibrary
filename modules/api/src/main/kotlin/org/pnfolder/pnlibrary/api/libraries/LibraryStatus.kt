package org.pnfolder.pnlibrary.api.libraries

/**
 * What one library is doing now and what an administrator chose for it.
 *
 * @property library the library
 * @property state where it stands
 * @property autoLoad whether it is loaded with the server; an administrator's choice, saved across restarts
 * @property error why the last load failed, or `null`
 * @property restartRequired it was switched off while loaded: Java cannot unload a jar in use, so it goes away at the next restart
 */
data class LibraryStatus(
    val library: ModuleLibrary,
    val state: LibraryState,
    val autoLoad: Boolean,
    val error: String? = null,
    val restartRequired: Boolean = false,
)
