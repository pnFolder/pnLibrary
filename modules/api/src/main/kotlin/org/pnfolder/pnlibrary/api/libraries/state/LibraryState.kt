package org.pnfolder.pnlibrary.api.libraries.state

/** Where a library stands in one plugin or module. */
enum class LibraryState {
    /** Downloaded, checked and joined its class loader. */
    LOADED,

    /** An administrator switched it off: it is not downloaded and not loaded until it is switched on again. */
    DISABLED,

    /** It could not be loaded; [LibraryStatus.error] says why and `retry` tries again. */
    FAILED,

    /** It belongs to another platform and is not needed here. */
    SKIPPED,
}
