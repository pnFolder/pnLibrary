package org.pnfolder.pnlibrary.api.libraries.state;

/** Where a library stands in one plugin or module. */
public enum LibraryState {
    /** Downloaded, checked and joined its class loader. */
    LOADED,
    /** An administrator switched it off: it is not downloaded and not loaded until it is switched on again. */
    DISABLED,
    /** It could not be loaded; {@link LibraryStatus#getError()} says why and {@code retry} tries again. */
    FAILED,
    /** It belongs to another platform and is not needed here. */
    SKIPPED
}
