package org.pnfolder.pnlibrary.api.config;

/** What to do with a list element whose {@code type} is not known to a polymorphic type. */
public enum UnknownTypePolicy {
    /** Reject the whole file with an error that names the element. */
    FAIL,

    /**
     * Skip the element, warn, and keep it in the file untouched. While an element is skipped the library does not
     * rewrite the file from memory ({@code save}, {@code update}, {@code resetToDefaults} fail), so nothing is lost.
     * When the type appears later, the file is read again and the element comes back. Applies to list elements.
     */
    SKIP
}
