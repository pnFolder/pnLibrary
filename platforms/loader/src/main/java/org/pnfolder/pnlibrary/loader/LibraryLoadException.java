package org.pnfolder.pnlibrary.loader;

import net.byteflux.libby.Library;

/** A runtime library could not be downloaded or loaded, even after every attempt. */
public final class LibraryLoadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String library;
    private final int attempts;

    LibraryLoadException(Library library, int attempts, Throwable cause) {
        super("Failed to load library " + id(library) + " after " + attempts + " attempt(s)", cause);
        this.library = id(library);
        this.attempts = attempts;
    }

    private static String id(Library library) {
        return library.getGroupId() + ":" + library.getArtifactId() + ":" + library.getVersion();
    }

    /** The library as {@code group:name:version}. */
    public String library() {
        return library;
    }

    /** How many times the download was tried. */
    public int attempts() {
        return attempts;
    }
}
