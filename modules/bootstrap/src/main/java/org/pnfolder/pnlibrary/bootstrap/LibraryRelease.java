package org.pnfolder.pnlibrary.bootstrap;

import java.net.URI;

/** One verified pnLibrary jar of a GitHub release: where to get it and what it must hash to. */
public final class LibraryRelease {
    private final String version;
    private final String fileName;
    private final URI uri;
    private final String sha256;
    private final long size;
    private final boolean stable;

    LibraryRelease(String version, String fileName, URI uri, String sha256, long size, boolean stable) {
        this.version = version;
        this.fileName = fileName;
        this.uri = uri;
        this.sha256 = sha256;
        this.size = size;
        this.stable = stable;
    }

    /** Returns the release version.
     * @return version without a leading {@code v} */
    public String version() { return version; }

    /** Returns the jar file name.
     * @return for example {@code pnLibrary-1.0.0-velocity-java17.jar} */
    public String fileName() { return fileName; }

    /** Returns the HTTPS download address.
     * @return the GitHub asset URL */
    public URI uri() { return uri; }

    /** Returns the SHA-256 published by GitHub.
     * @return 64 lowercase hex characters */
    public String sha256() { return sha256; }

    /** Returns the size published by GitHub.
     * @return the size in bytes */
    public long size() { return size; }

    /** Returns whether this is the latest stable release rather than the exact minimum version.
     * @return {@code true} for the latest stable release */
    public boolean stable() { return stable; }
}
